# Guia de Implementacao para Agente - Perfil e Preferencias de Match

## Objetivo

Implementar e manter o fluxo em duas etapas do app:

1. `user_profile`: dados sobre a propria pessoa.
2. `user_match_preferences`: dados sobre o que a pessoa busca para match.

Regra central:

- nomes de tabelas, colunas, entidades, DTOs e rotas ficam em ingles;
- mensagens, descricoes de lookup e textos pensados para exibicao ficam em portugues.

## Estrutura persistida

### Tabelas principais

- `user_profiles`
  - `id`
  - `fk_user`
  - `bio`
  - `fk_gender`
  - `fk_autonomy_level`
  - `fk_energy_level`

- `user_match_preferences`
  - `id`
  - `fk_user_profile`
  - `fk_connection_type`
  - `accessibility_need_similarity`
  - `autonomy_compatibility`
  - `lifestyle_similarity`
  - `energy_level_similarity`
  - `min_age`
  - `max_age`
  - `max_match_distance_km`

- `user_coordinates`
  - `id`
  - `fk_user_profile`
  - `latitude`
  - `longitude`
  - `active`

### Tabelas auxiliares

- `genders`
- `disabilities`
- `accessibility_needs`
- `autonomy_levels`
- `communication_forms`
- `lifestyle_types`
- `energy_levels`
- `interest_types`
- `connection_types`

### Tabelas relacionais

- `user_profile_disabilities`
- `user_profile_accessibility_needs`
- `user_profile_communication_forms`
- `user_profile_lifestyle_types`
- `user_profile_interest_types`
- `user_match_preference_desired_genders`

### Regras de negocio importantes

- `disabilities` pode ficar vazio, porque pessoas sem deficiencia tambem podem usar o app.
- `user_coordinates` permite historico, mas so pode existir uma coordenada ativa por perfil ao mesmo tempo.
- os `PUT` de perfil e preferencia funcionam como substituicao completa da fatia enviada; o cliente deve enviar o estado atual completo daquela etapa.
- a faixa etaria desejada fica em `minAge` e `maxAge` dentro de `user_match_preferences`; os dois campos sao opcionais, mas quando enviados precisam ser maiores ou iguais a `18`, e `minAge` nao pode ser maior que `maxAge`.
- os enums de similaridade ficam em ingles no backend:
  - `ANY`
  - `SIMILAR`
  - `DIFFERENT`

## Endpoints

Todas as rotas abaixo exigem usuario autenticado com role `user`.

### Perfil do usuario

- `GET /users/me/profile`
  - retorna o perfil atual do usuario.
  - se ainda nao existir registro, retorna payload vazio da estrutura esperada.

- `PUT /users/me/profile`
  - cria ou atualiza o `user_profile`.
  - tambem substitui listas relacionais e a coordenada ativa.

### Preferencias de match

- `GET /users/me/match-preferences`
  - retorna as preferencias atuais.
  - se ainda nao existir registro, retorna payload vazio da estrutura esperada, incluindo a faixa etaria desejada.

- `PUT /users/me/match-preferences`
  - cria ou atualiza o `user_match_preferences`.
  - permite calibrar a faixa etaria das pessoas buscadas via `minAge` e `maxAge`.

### Fluxo inicial do app

- `GET /users/me/profile/completion`
  - endpoint unico para o app decidir em qual etapa do onboarding o usuario esta.
  - retorna:
    - `profileCompleted`
    - `matchPreferencesCompleted`
    - `fullyCompleted`
    - `missingProfileFields`
    - `missingMatchPreferenceFields`

### Dados auxiliares para formularios

- `GET /users/me/profile/options`
  - retorna todas as listas de lookup em uma chamada unica.
  - inclui tambem as opcoes de similaridade em portugues.

## Regras de completude

### Etapa 1 - Perfil minimo

Considerar concluido quando houver:

- genero
- forma de comunicacao
- estilo de vida
- interesses

Observacao:

- `tipo de deficiência` faz parte da etapa, mas pode ser lista vazia. Se o perfil existe e os demais obrigatorios foram preenchidos, a etapa continua valida mesmo sem itens em `disabilities`.

### Etapa 2 - Preferencias minimas

Considerar concluido quando houver:

- distancia maxima do match
- tipo de conexao
- estilo de vida preferido (`lifestyleSimilarity`)
- ao menos um genero desejado

Observacao:

- a faixa etaria desejada e opcional no onboarding atual; se o produto passar a exigir `minAge` e `maxAge`, o endpoint `GET /users/me/profile/completion` precisa ser atualizado.

## Fluxo esperado do app

1. Usuario faz cadastro base em `POST /auth/signup`.
2. Usuario entra no app autenticado.
3. App chama `GET /users/me/profile/completion`.
4. Se `profileCompleted` for falso:
   - app carrega `GET /users/me/profile/options`;
   - app exibe a etapa 1;
   - app envia `PUT /users/me/profile`.
5. Se `profileCompleted` for verdadeiro e `matchPreferencesCompleted` for falso:
   - app pode reutilizar `GET /users/me/profile/options`;
   - app exibe a etapa 2;
   - app envia `PUT /users/me/match-preferences`.
6. Se `fullyCompleted` for verdadeiro:
   - app libera o fluxo principal.
7. Para edicao posterior:
   - usar `GET /users/me/profile` e `GET /users/me/match-preferences` para preencher formularios;
   - salvar novamente com os mesmos `PUT`.

## Payloads esperados

### Exemplo de `PUT /users/me/profile`

```json
{
  "bio": "Gosto de tecnologia e eventos acessiveis.",
  "genderId": 1,
  "disabilityIds": [1, 2],
  "accessibilityNeedIds": [2, 3],
  "autonomyLevelId": 2,
  "communicationFormIds": [1, 4],
  "lifestyleTypeIds": [1, 4],
  "energyLevelId": 2,
  "interestTypeIds": [2, 3, 4],
  "location": {
    "latitude": -23.55052,
    "longitude": -46.633308
  }
}
```

### Exemplo de `PUT /users/me/match-preferences`

```json
{
  "connectionTypeId": 1,
  "accessibilityNeedSimilarity": "ANY",
  "autonomyCompatibility": "SIMILAR",
  "lifestyleSimilarity": "SIMILAR",
  "energyLevelSimilarity": "ANY",
  "minAge": 25,
  "maxAge": 35,
  "maxMatchDistanceKm": 30,
  "desiredGenderIds": [1, 3]
}
```

## Arquivos principais desta implementacao

- `src/main/java/br/com/unify/matchable/user/entity/*`
- `src/main/java/br/com/unify/matchable/user/dto/*`
- `src/main/java/br/com/unify/matchable/user/services/UserProfileService.java`
- `src/main/java/br/com/unify/matchable/user/services/UserProfileServiceImplementation.java`
- `src/main/java/br/com/unify/matchable/user/resources/UserProfileResource.java`
- `src/main/resources/import.sql`
- `src/test/java/br/com/unify/matchable/user/resources/UserProfileResourceTest.java`

## Validacoes importantes

- `maxMatchDistanceKm` deve ser maior que zero.
- `minAge` e `maxAge`, quando informados, devem ser maiores ou iguais a `18`.
- `minAge` nao pode ser maior que `maxAge`.
- latitude deve estar entre `-90` e `90`.
- longitude deve estar entre `-180` e `180`.
- IDs de tabelas auxiliares devem existir.

## Checklist para evolucao futura

1. Se adicionar novo campo obrigatorio de onboarding, atualizar o calculo de `completion`.
2. Se adicionar nova tabela auxiliar, atualizar `import.sql` e `GET /users/me/profile/options`.
3. Se mudar a semantica de `PUT`, alinhar frontend e este guia.
4. Se quiser permitir limpar campos opcionais sem enviar a fatia inteira, criar `PATCH` com semantica parcial explicita.