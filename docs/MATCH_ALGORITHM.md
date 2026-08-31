# Algoritmo de Match do Unify — especificação implementada

> Versão 2. Substitui a versão 1, que descrevia a intenção original.
> O briefing original permanece em `MATCH_ALG_GUIDE.md` como referência histórica.
>
> - Implementação: `src/main/java/br/com/unify/matchable/user/services/UserMatchServiceImplementation.java`
> - Pesos e constantes: `src/main/java/br/com/unify/matchable/user/services/MatchScoringPolicy.java`
> - Limpeza de recusas: `src/main/java/br/com/unify/matchable/user/services/UserMatchCleanupService.java`
> - Entidade de decisão: `src/main/java/br/com/unify/matchable/user/entity/UserPossibleMatch.java`
> - Testes: `src/test/java/.../user/services/UserMatchServiceImplementationTest.java`,
>   `.../MatchScoringPolicyTest.java`

---

## 1. Visão geral em duas etapas

```
POST /users/me/matches/discovery
        │
        ├─ Etapa 1: filtros exclusivos (SQL nativo, PostgreSQL)
        │     gênero desejado → decisões já tomadas → bounding box + haversine → faixa etária
        │     (expansão progressiva +5 / +10 anos até PRESELECTION_LIMIT = 250 candidatos)
        │
        ├─ Etapa 2: scoring em memória (0..100, normalizado por cobertura)
        │     10 fatores → soma ponderada → normalização → penalidades
        │
        └─ Etapa 3: feed orgânico (TARGET_MATCH_COUNT = 50)
              blocos de 4 ranqueados + 1 de descoberta; convites recebidos inseridos
              em posição aleatória
```

A etapa 1 **elimina**; a etapa 2 **ordena**; a etapa 3 **diversifica**. Nenhum fator do scoring elimina
candidato: reciprocidade não atendida vira penalidade de ranking, nunca filtro (regra do
`MATCH_ALG_GUIDE.md`).

---

## 2. Etapa 1 — Filtros exclusivos

Três consultas nativas, todas construídas em `UserMatchServiceImplementation`:

| Método | Quando é usada |
|---|---|
| `executeCandidateQuery` | Usuário atual tem coordenada ativa (modo `geo`) |
| `executeNoLocationModeQuery` | Usuário atual **sem** coordenada ativa (modo `no-location`) |
| `executeNoCoordinateCandidateQuery` | Complemento por cota: candidatos sem coordenada ativa |

### 2.1 Gênero desejado

`up.fk_gender in (:gender0, ..., :genderN) or up.fk_gender = 4`.
O id 4 ("prefiro não informar") **sempre** entra, em qualquer configuração de preferência.
O gênero do **próprio** usuário não participa da consulta (ver 5.4).

### 2.2 Exclusão de perfis já decididos e de recusas em carência

Um único `NOT EXISTS` sobre `user_possible_matches` substitui a antiga lista
`up.id not in (:excluded0 .. :excluded499)` (plano de execução ruim e risco de estourar o limite de
parâmetros do driver):

```sql
and not exists (
    select 1 from user_possible_matches m
    where ((m.fk_starter_user_profile = :currentProfileId and m.fk_pending_user_profile = up.id)
        or (m.fk_pending_user_profile = :currentProfileId and m.fk_starter_user_profile = up.id))
      and ((m.declined_at is null
              and (m.fk_starter_user_profile = :currentProfileId or m.pending_accepted is not null))
        or (m.declined_at is not null and m.declined_at >= :declineThreshold))
)
```

Leitura da regra:

- linha **sem** `declined_at` = relação viva (eu já decidi, ou o outro lado já respondeu) ⇒ exclui sempre;
- linha **com** `declined_at` ⇒ exclui apenas enquanto a carência não expira. Depois disso o perfil volta ao
  pool com a penalidade de reapresentação (ver 3.4);
- convite recebido ainda não respondido **não** é excluído: ele é o `priorityInbound` da etapa 3.

A lista `alreadyUsedProfileIds` enviada pelo cliente continua sendo filtrada **em memória** depois da
consulta, porque é estado de sessão do aplicativo, não estado do servidor.

### 2.3 Bounding box + haversine

O haversine (`acos/cos/sin`) roda apenas sobre os candidatos que sobrevivem a um pré-filtro de range,
que usa índice:

```sql
where c.latitude  between :currentLatitude  - (:maxMatchDistanceKm / 111.045)
                      and :currentLatitude  + (:maxMatchDistanceKm / 111.045)
  and c.longitude between :currentLongitude - (:maxMatchDistanceKm / (111.045 * greatest(cos(radians(:currentLatitude)), 0.01)))
                      and :currentLongitude + (:maxMatchDistanceKm / (111.045 * greatest(cos(radians(:currentLatitude)), 0.01)))
```

`111.045` é a distância aproximada em km de um grau de latitude; o `greatest(cos(...), 0.01)` evita
divisão por zero perto dos polos. O resultado final ainda é conferido por
`where distance_km <= :maxMatchDistanceKm`, então o bounding box é apenas um pré-filtro conservador.

### 2.4 Faixa etária como range de datas (indexável)

```sql
-- ANTES (expressão sobre a coluna, força seq scan em users):
--   and date_part('year', age(current_date, u.birthdate)) >= :minAge
-- DEPOIS:
and u.birthdate <= current_date - make_interval(years => :minAge)
and u.birthdate >  current_date - make_interval(years => :maxAgePlusOne)
```

`birthdate` fica sozinha de um lado do operador, então `idx_users_verified_birthdate` é usado.
`make_interval` é específico do PostgreSQL — assim como o `date_part`/`age` anterior; o projeto é
PostgreSQL-only.

### 2.5 Expansão progressiva de faixa etária

`buildAgeExpansions` gera `[0, +5, +10]`. A cada passo os resultados são acumulados por
`putIfAbsent`; o laço para assim que `PRESELECTION_LIMIT = 250` é atingido. `minAge` nunca desce
abaixo de `MINIMUM_ALLOWED_AGE = 18`.

### 2.6 Query complementar de candidatos sem coordenada (cota)

Quando o usuário atual tem coordenada mas o pool ficou abaixo de 250, roda-se uma segunda consulta
para perfis **sem** `user_coordinates.active = true`, limitada a
`ceil(250 × unify.match.no-location-candidate-quota)` ≈ 38 perfis, ordenada por `up.id desc`
(o id é UUIDv7, logo os perfis mais recentes vêm primeiro). Esses candidatos entram no scoring com
`distanceKm = null`.

---

## 3. Etapa 2 — Scoring

### 3.1 Tabela de pesos

| Fator | Peso anterior | **Peso atual** | Justificativa |
|---|---:|---:|---|
| Formas de comunicação | 25 | **22** | Continua o maior peso: sem canal comum não existe relação. A fórmula deixou de ser "semelhança de conjuntos" e virou "viabilidade de canal", então 22 já expressa a prioridade sem penalizar perfis ricos. |
| Interesses | 15 | **18** | É o fator que mais prediz a primeira mensagem e a retenção. Com a sobreposição corrigida, 18 pontos passam a diferenciar de verdade. |
| Tipo de conexão | 15 | **12** | Continua alto, mas deixou de ser um cliff de 15 pontos: agora tem faixa parcial. |
| Necessidades de acessibilidade | 10 | **12** | Propósito declarado do produto; com autonomia soma 20, como o `MATCH_ALG_GUIDE.md` prescreve. |
| Estilo de vida | 10 | **10** | Mantido. Boa relação sinal/ruído. |
| Autonomia | 10 | **8** | Isoladamente é o fator com maior risco de discriminação ("medicalized matching"); importa combinado à acessibilidade, não sozinho. |
| Linguagem do amor | 5 | **6** | Único fator explicitamente romântico; diferencia bem quando o tipo de conexão é de relacionamento. |
| Nível de energia | 5 | **5** | Mantido. |
| Distância | 1–3 | **0–4** | Curva contínua em vez de 3 degraus. |
| Diferença de idade | 0–2 | **0–3** | Curva contínua; contrapeso da expansão de faixa etária. |
| **Total** | **100** | **100** | Invariante coberta por `MatchScoringPolicyTest.weightsMustSumExactlyOneHundred`. |

### 3.2 Fórmula de cada fator

Cada fator devolve um `FactorScore(applicable, obtained, weight)`. `applicable = false` significa
"não há informação suficiente dos dois lados": o peso sai do numerador **e** do denominador.

| Fator | Fórmula | Não avaliável quando |
|---|---|---|
| Comunicação | `0` se não há canal comum; senão `0,60 + 0,40 × (compartilhados / min(\|A\|,\|B\|))` | um dos lados não declarou nenhuma forma |
| Interesses | `overlap × (0,75 + 0,25 × breadth)`, com `overlap = compartilhados / min` e `breadth = min / max` | um dos lados sem interesses |
| Tipo de conexão | igual → 100%; par parcial → 50%; senão 0 | qualquer lado sem tipo definido |
| Acessibilidade | ausência mútua → 100%; senão preferência recíproca | preferência ausente dos dois lados |
| Estilo de vida / Linguagem do amor | preferência recíproca sobre conjuntos | preferência ausente dos dois lados, ou conjunto vazio |
| Autonomia / Energia | preferência recíproca por distância de nível | preferência ausente dos dois lados, ou nível ausente |
| Distância | 1,0 até 10 km, decaimento linear até 0 em 120 km | `distanceKm == null` (algum lado sem coordenada) |
| Diferença de idade | 1,0 até 3 anos, decaimento linear até 0 em 20 anos | qualquer idade nula |

**Comunicação é praticidade, não semelhança.** A fórmula anterior dividia por `max(|A|,|B|)`: quem
declarava 5 formas de comunicação contra alguém que declarava só "texto" recebia `1/5 × 25 = 5` pontos,
mesmo com um canal comum perfeitamente viável. Num app de inclusão isso penalizava justamente os perfis
mais adaptáveis. Hoje divide-se por `min(|A|,|B|)` e o simples fato de existir um canal comum já vale 60%
do peso.

**Preferência recíproca.** Para cada direção declarada calcula-se
`compartilhados / max(|origem|,|destino|)`; `SIMILAR` usa a razão, `DIFFERENT` usa `1 − razão`, `ANY`
usa `NEUTRAL_PREFERENCE_RATIO = 0,65` do peso (antes eram 0,75 e 0,70 conforme o fator, sem justificativa
documentada). Quando **nenhuma** das duas direções tem preferência, o fator é não avaliável — antes ele
devolvia `peso × neutro`, ou seja, pontos de graça por não preencher nada.

**Distância de nível** (autonomia e energia), com `LEVEL_*_RATIO` de `MatchScoringPolicy`:

| Preferência | distância 0 | distância 1 | distância ≥ 2 |
|---|---|---|---|
| `SIMILAR` | 100% | 60% | 20% |
| `DIFFERENT` | 0% | 60% | 100% |

**Mapa de compatibilidade parcial de tipo de conexão.** Catálogo real
(`src/main/resources/import.sql`): `1 = Amizade`, `2 = Relacionamento`, `3 = Networking`,
`4 = Comunidade`. Decisão implementada em `COMPATIBLE_CONNECTION_TYPES`:

| Par | Compatibilidade |
|---|---|
| 1 ↔ 3 (Amizade ↔ Networking) | parcial (50% do peso) |
| 1 ↔ 4 (Amizade ↔ Comunidade) | parcial (50% do peso) |
| 3 ↔ 4 (Networking ↔ Comunidade) | parcial (50% do peso) |
| 2 (Relacionamento) com qualquer outro | 0 |

Racional: os três tipos platônicos são intercambiáveis o suficiente para valer meia pontuação — quem
procura amizade aceita razoavelmente uma conexão de comunidade ou de rede profissional. "Relacionamento"
não tem par parcial: cruzar quem procura namoro com quem procura amizade é exatamente o erro que este
fator existe para evitar. O mapa vive em código, e não no banco, porque o catálogo é pequeno e estável;
se ele crescer, mover para uma coluna `compatible_with` em `connection_types`.
`UserMatchServiceImplementationTest.partialConnectionTypeMapOnlyReferencesCatalogIds` falha se o mapa
citar um id fora do catálogo ou perder a simetria.

### 3.3 Normalização por cobertura e teto de baixa confiança

```
somaObtida  = Σ obtido_i     (apenas fatores applicable)
somaPesos   = Σ peso_i       (apenas fatores applicable)
scoreBruto  = 100 × somaObtida / somaPesos
cobertura   = somaPesos / 100
se cobertura < 0,40  →  score = min(scoreBruto, 70)
score       = max(0, min(100, score − penalidades))
```

Sem normalização, um perfil que não preencheu o tipo de conexão perdia 12 pontos absolutos enquanto um
perfil que não preencheu **nenhuma** preferência ganhava ~25 pontos "neutros". A normalização faz o score
responder à **qualidade** das informações em comum; a cobertura responde pela **quantidade**, via teto de
baixa confiança (`MIN_CONFIDENT_COVERAGE = 0,40`, `LOW_CONFIDENCE_SCORE_CAP = 70`).

Se nenhum fator for avaliável, o score é 0.

### 3.4 Penalidades de reciprocidade e reapresentação

Aplicadas **depois** da normalização, em pontos absolutos:

| Penalidade | Pontos | Quando |
|---|---:|---|
| `PENALTY_GENDER_NOT_RECIPROCAL` | 8 | A preferência de gênero do candidato não inclui o gênero do usuário atual (gênero 4 nunca penaliza) |
| `PENALTY_AGE_NOT_RECIPROCAL` | 5 | A faixa etária do candidato exclui a idade do usuário atual |
| `PENALTY_RESHOWN_AFTER_COOLDOWN` | 10 | Perfil recusado que voltou ao pool após a carência |

Reciprocidade **reduz o ranking, nunca elimina o perfil** — o score fica em `max(0, ...)`.

### 3.5 Tabela de classificação

| Score | Rótulo |
|---|---|
| 85–100 | Altíssima compatibilidade |
| 70–84 | Alta compatibilidade |
| 50–69 | Compatível |
| 35–49 | Compatibilidade parcial |
| 0–34 | Baixa compatibilidade |

---

## 4. Etapa 3 — Feed orgânico

- `TARGET_MATCH_COUNT = 50` perfis por chamada.
- 4/5 das vagas restantes vão para os **ranqueados** (maior score primeiro; empate resolvido pela menor
  distância, com `null` no fim).
- 1/5 vai para **descoberta**: sorteio entre candidatos com score em
  `[MINIMUM_DISCOVERY_SCORE, PREFERRED_DISCOVERY_MAX_SCORE)` = `[35, 75)`; se esse pool não encher a cota,
  completa-se com qualquer candidato acima de 35.
- A intercalação é feita em blocos: 4 ranqueados, 1 de descoberta, e assim por diante.
- **Convites recebidos pendentes** (`priorityInbound`) são inseridos em posição aleatória do feed, para
  que a pessoa não perceba um padrão fixo; se o feed estourar o limite, o último item é descartado.
- As faixas 35/75 foram recalibradas em relação às antigas 30/70 porque a normalização deslocou a
  distribuição para cima. Se, com dados reais, mais de 60% dos perfis passarem de 75, subir
  `PREFERRED_DISCOVERY_MAX_SCORE` para 80.

---

## 5. Fallbacks explícitos

### 5.1 Usuário sem preferências → defaults em memória

`resolveMatchPreference` não devolve mais 400. Se existe uma `UserMatchPreference`, as associações LAZY
são inicializadas, a entidade é **desanexada** (`entityManager.detach`) e só então os campos nulos recebem
default. O detach é obrigatório: `discoverPotentialMatches` roda dentro da transação do resource, e mutar
uma entidade gerenciada persistiria preferências que o usuário nunca escolheu. Se não existe preferência
alguma, `buildDefaultPreference` monta um objeto transiente com todas as similaridades em `ANY`.

### 5.2 Usuário sem coordenada → modo `no-location`

Sem coordenada ativa e com `unify.match.allow-discovery-without-location=true`, a busca roda sem bounding
box e sem haversine; `distanceKm` fica `null` para todos os candidatos, o peso 4 sai do denominador e
ninguém é penalizado. A resposta traz os cabeçalhos:

```
X-Unify-Discovery-Mode: geo | no-location
X-Unify-Discovery-Count: <quantidade de perfis>
```

O corpo continua sendo `List<UUID>` — nenhuma quebra de contrato para o frontend.

### 5.3 Candidato sem coordenada → cota, não exclusão

O `INNER JOIN` com `user_coordinates` fazia com que quem nunca concedeu permissão de GPS jamais aparecesse
para ninguém. Agora esses perfis entram por cota (ver 2.6), com `distanceKm = null`.

### 5.4 Perfil sem gênero → não bloqueia a busca

`validateDiscoveryState` exigia `currentProfile.gender`, mas o gênero do usuário atual **não é usado em
nenhum ponto da consulta** (o filtro é sobre `up.fk_gender`, o gênero do candidato). A validação foi
removida. Também saíram as exigências de distância máxima e de gêneros desejados, que agora têm default.
Restam duas: idade calculável (data de nascimento preenchida) e, se
`allow-discovery-without-location=false`, coordenada ativa.

---

## 6. Ciclo de vida da decisão de match

```
sem registro ──aceite──▶ UserPossibleMatch(starterAccepted=true, pendingAccepted=null)
             ──recusa──▶ UserPossibleMatch(starterAccepted=false, declinedAt=agora,
                                           declinedByProfile=quem recusou)

convite recebido ──aceite──▶ pendingAccepted=true  ⇒ match mútuo
                 ──recusa──▶ pendingAccepted=false, declinedAt=agora

recusa antiga (fora da carência) ──nova decisão──▶ registro sobrescrito, createdAt=agora
```

- **A recusa de um perfil novo passa a ser persistida.** Antes, `registerDecision` lançava
  `IllegalArgumentException` ("Não existe um match pendente deste perfil para registrar uma recusa") e a
  recusa vivia apenas no `AsyncStorage` do aplicativo: trocar de aparelho ou limpar dados trazia todos os
  perfis recusados de volta.
- **Carência:** `unify.match.decline-cooldown-days` (default 30). Durante a carência o perfil não aparece
  no discovery; depois dela volta com a penalidade de 10 pontos.
- **Limpeza:** `UserMatchCleanupService` roda a cada 24 h e apaga **apenas** recusas com
  `declined_at < agora − carência`. A versão anterior deletava toda linha `pendingAccepted = false` a cada
  10 h, o que fazia qualquer perfil recusado voltar ao feed em no máximo 10 horas.
- Decidir duas vezes o mesmo perfil (sem recusa expirada no meio) devolve 409
  ("Você já registrou uma decisão para este perfil").

---

## 7. Índices e performance

Criados em `db/migration/V8__match_discovery_indexes.sql`:

| Índice | Serve a |
|---|---|
| `idx_user_profiles_gender` | Filtro de gênero do candidato |
| `idx_users_verified_birthdate` (parcial, `verified = true`) | Range de `birthdate` |
| `idx_user_coordinates_active_lat_lon` (parcial, `active = true`) | Bounding box |
| `idx_user_coordinates_profile_active` (parcial, `active = true`) | Lookup de coordenada ativa |
| `idx_user_possible_matches_starter_pending_state` | `NOT EXISTS` de exclusão (direção iniciada) |
| `idx_user_possible_matches_pending_starter_state` | `NOT EXISTS` de exclusão (direção recebida) |
| `idx_user_possible_matches_declined_at` (parcial, `declined_at is not null`) | Limpeza de recusas expiradas (V7) |

Outras medidas de performance:

- `quarkus.hibernate-orm.fetch.batch-size=50` em `application.properties`. As 6 coleções `@ManyToMany`
  usadas no scoring continuam LAZY, mas são carregadas em lotes de 50 em vez de uma query por perfil.
- `loadProfiles` faz **uma** query com `left join fetch` de `user`, `matchPreference` e
  `matchPreference.connectionType`, no lugar de até 250 `findById` em laço. As coleções não entram no
  fetch join de propósito: 6 fetch joins gerariam produto cartesiano.
- Custo esperado por chamada de discovery: 1 a 3 consultas de candidatos (expansão de idade) + até 3
  complementares sem coordenada + 1 de perfis + ~6 consultas de coleções em lote — contra as ~1.750
  queries do desenho anterior.

---

## 8. Divergências conhecidas em relação ao `MATCH_ALG_GUIDE.md`

- O guia previa **acessibilidade 20** sem linha para autonomia; a implementação usa **12 + 8 = 20**,
  separando "necessidades" de "autonomia" para não medicalizar o match.
- O guia sugere **pré-cálculo assíncrono** (tabela `user_match_scores`): **não implementado**.
  Custo/benefício ruim na escala atual — reavaliar acima de ~20 mil usuários ativos.
- O guia sugere o padrão **Strategy** (`MatchFactorScorer`): **não implementado**. O número de fatores é
  estável e `MatchScoringPolicy` já centraliza toda a configuração.
- O guia trata "Connection Type" como conjunto; o modelo atual (`UserMatchPreference.connectionType`) é um
  `@ManyToOne` único, então a sobreposição parcial é expressa pelo mapa de proximidade semântica da
  seção 3.2.

---

## 9. Parâmetros de configuração

Todos em `src/main/resources/application.properties`.

| Propriedade | Default | Efeito |
|---|---|---|
| `unify.match.default-max-distance-km` | 60 | Distância máxima quando o usuário não definiu uma |
| `unify.match.default-min-age` | 18 | Idade mínima default da busca |
| `unify.match.default-max-age` | 99 | Idade máxima default da busca |
| `unify.match.allow-discovery-without-location` | true | Permite o modo `no-location` em vez de erro 400 |
| `unify.match.no-location-candidate-quota` | 0.15 | Fração de `PRESELECTION_LIMIT` reservada a candidatos sem coordenada |
| `unify.match.decline-cooldown-days` | 30 | Carência da recusa, no discovery e na limpeza agendada |
| `quarkus.hibernate-orm.fetch.batch-size` | 50 | Tamanho do lote de carga das coleções LAZY |

Constantes de código (não configuráveis): `TARGET_MATCH_COUNT = 50`, `PRESELECTION_LIMIT = 250`,
`MAX_AGE_EXPANSION_YEARS = 10`, `AGE_EXPANSION_STEP_YEARS = 5`, `MINIMUM_ALLOWED_AGE = 18`, e toda a
tabela de pesos em `MatchScoringPolicy`.
