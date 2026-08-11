# Guia Completo para Agente - Conta, Perfil e Preferencias de Match

## Objetivo

Este guia descreve, em detalhe, o comportamento atual do backend para:

1. criacao da conta base do usuario;
2. autenticacao e acesso ao usuario corrente;
3. onboarding em duas etapas (`user_profile` e `user_match_preferences`);
4. edicao posterior de perfil e preferencias de match;
5. validacoes, regras de completude e pontos de extensao.

Use este arquivo quando um agente precisar alterar, depurar, documentar ou expandir qualquer parte do fluxo de conta, perfil complementar e preferencias de match.

## Convencoes

- nomes de entidades, tabelas, colunas, DTOs e endpoints ficam em ingles;
- mensagens de validacao e descricoes mostradas ao usuario ficam em portugues;
- os `PUT` de onboarding seguem semantica de substituicao completa da fatia enviada;
- `birthdate` pertence a `users`, nao a `user_profiles`;
- a faixa etaria desejada de match pertence a `user_match_preferences`, nao a `users`.

## Modelo de dados

### Conta base

- `users`
  - `id`
  - `name`
  - `last_name`
  - `email`
  - `cellphone`
  - `password`
  - `birthdate`
  - `verified`
  - `last_updated_at`

Responsabilidade:

- guardar a identidade principal da conta;
- controlar maioridade no cadastro via `birthdate`;
- servir como raiz para `UserProfile`.

### Perfil complementar

- `user_profiles`
  - `id`
  - `fk_user`
  - `bio`
  - `fk_gender`
  - `fk_autonomy_level`
  - `fk_energy_level`

Relacionamentos do perfil:

- `user_profile_disabilities`
- `user_profile_accessibility_needs`
- `user_profile_communication_forms`
- `user_profile_lifestyle_types`
- `user_profile_interest_types`
- `user_coordinates`

Responsabilidade:

- guardar dados sobre a propria pessoa;
- servir de dono da coordenada ativa e das preferencias de match.

### Preferencias de match

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

Relacionamentos das preferencias:

- `user_match_preference_desired_genders`

Responsabilidade:

- guardar o que o usuario busca em outras pessoas;
- guardar filtros numericos de distancia e faixa etaria;
- guardar afinidades por enum (`ANY`, `SIMILAR`, `DIFFERENT`).

### Coordenadas

- `user_coordinates`
  - `id`
  - `fk_user_profile`
  - `latitude`
  - `longitude`
  - `active`

Responsabilidade:

- manter historico de localizacao;
- garantir apenas uma coordenada ativa por perfil por vez via indice parcial `uq_user_coordinates_active_profile`.

### Lookups

As opcoes de formulario sao carregadas de tabelas auxiliares seeded em `import.sql`:

- `genders`
- `disabilities`
- `accessibility_needs`
- `autonomy_levels`
- `communication_forms`
- `lifestyle_types`
- `energy_levels`
- `interest_types`
- `connection_types`

## Relacoes entre entidades

- `User` -> `UserProfile`: `1:1`
- `UserProfile` -> `UserMatchPreference`: `1:1`
- `UserProfile` -> `UserCoordinates`: `1:N`
- `UserProfile` -> lookups de perfil: varios `N:N` e `N:1`
- `UserMatchPreference` -> `ConnectionType`: `N:1`
- `UserMatchPreference` -> `Gender`: `N:N` para generos desejados

## Arquivos que controlam o fluxo

- `src/main/java/br/com/unify/matchable/auth/resources/AuthResource.java`
- `src/main/java/br/com/unify/matchable/auth/dto/SignUpRequest.java`
- `src/main/java/br/com/unify/matchable/user/resources/UserResource.java`
- `src/main/java/br/com/unify/matchable/user/resources/UserProfileResource.java`
- `src/main/java/br/com/unify/matchable/user/services/ServicesUserImplementation.java`
- `src/main/java/br/com/unify/matchable/user/services/UserProfileServiceImplementation.java`
- `src/main/java/br/com/unify/matchable/user/entity/User.java`
- `src/main/java/br/com/unify/matchable/user/entity/UserProfile.java`
- `src/main/java/br/com/unify/matchable/user/entity/UserMatchPreference.java`
- `src/main/resources/import.sql`
- `src/test/java/br/com/unify/matchable/user/resources/UserProfileResourceTest.java`

## Ciclo completo do usuario

### 1. Criacao da conta base

Endpoint principal:

- `POST /auth/signup`

Request atual:

```json
{
  "name": "Pedro",
  "lastName": "Ambiel",
  "email": "pedro@example.com",
  "password": "Senha@123",
  "birthdate": "1998-11-08"
}
```

Comportamento:

- valida email obrigatorio;
- valida `birthdate` obrigatorio;
- valida formato de email;
- valida comprimento minimo e complexidade da senha;
- valida maioridade minima de `18` anos;
- impede criar usuario com email ja existente;
- faz hash da senha com bcrypt;
- persiste `User` com `verified = false`;
- dispara emissao do codigo de verificacao de email;
- retorna `202 Accepted` quando a conta base e criada com sucesso.

Importante:

- `POST /auth/signup` nao cria `user_profile` nem `user_match_preferences`;
- o onboarding complementar acontece depois da autenticacao.

### 2. Verificacao de email

Endpoints relacionados:

- `POST /auth/verify-email`
- `POST /auth/resend-email-verification`

Responsabilidade:

- liberar o login pleno de uma conta recem-criada;
- manter o fluxo de criacao separado do fluxo de preenchimento do perfil.

### 3. Autenticacao

Endpoints relacionados:

- `POST /auth/signin`
- `POST /auth/refresh`
- `POST /auth/logout`

Responsabilidade:

- obter tokens JWT para acessar rotas autenticadas;
- manter o contexto do usuario corrente para `/users/me` e rotas de onboarding.

### 4. Leitura do usuario corrente

Endpoint:

- `GET /users/me`

Payload retornado:

```json
{
  "id": "019dbf9a-5a8e-72de-85cb-8426b424c6fe",
  "name": "Pedro",
  "lastName": "Ambiel",
  "email": "pedro@example.com",
  "cellphone": ""
}
```

Importante:

- este endpoint retorna apenas o resumo da conta base;
- ele nao retorna `birthdate`, nem `user_profile`, nem `user_match_preferences`.

## Endpoints de onboarding e edicao

Todas as rotas abaixo exigem usuario autenticado com role `user`.

### `GET /users/me/profile`

Objetivo:

- ler a fatia complementar do perfil.

Se ainda nao existir perfil:

- retorna a estrutura vazia esperada, em vez de `404`.

Shape de resposta:

```json
{
  "id": null,
  "bio": null,
  "gender": null,
  "disabilities": [],
  "accessibilityNeeds": [],
  "autonomyLevel": null,
  "communicationForms": [],
  "lifestyleTypes": [],
  "energyLevel": null,
  "interestTypes": [],
  "activeLocation": null
}
```

### `PUT /users/me/profile`

Objetivo:

- criar ou atualizar `UserProfile`.

Request atual:

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

Semantica:

- cria `UserProfile` se ainda nao existir;
- substitui completamente todas as colecoes enviadas;
- `Set` vazio limpa o relacionamento correspondente;
- `null` em campos singulares opcionais limpa o valor;
- desativa todas as coordenadas anteriores antes de processar a nova localizacao;
- se `location` vier `null`, nenhuma nova coordenada ativa e criada;
- se `location` vier preenchida, uma nova coordenada ativa entra no inicio da lista historica.

Validacoes:

- ids de lookup precisam existir;
- latitude deve estar entre `-90` e `90`;
- longitude deve estar entre `-180` e `180`;
- latitude e longitude devem ser informadas juntas.

### `GET /users/me/match-preferences`

Objetivo:

- ler a fatia de preferencias de match.

Se ainda nao existir perfil ou preferencia:

- retorna a estrutura vazia esperada, em vez de `404`.

Shape de resposta:

```json
{
  "id": null,
  "connectionType": null,
  "accessibilityNeedSimilarity": null,
  "autonomyCompatibility": null,
  "lifestyleSimilarity": null,
  "energyLevelSimilarity": null,
  "minAge": null,
  "maxAge": null,
  "maxMatchDistanceKm": null,
  "desiredGenders": []
}
```

### `PUT /users/me/match-preferences`

Objetivo:

- criar ou atualizar `UserMatchPreference`.

Request atual:

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

Semantica:

- cria `UserProfile` vazio se ele ainda nao existir, porque a preferencia precisa de um dono (`fk_user_profile`);
- cria `UserMatchPreference` se ainda nao existir;
- substitui completamente `desiredGenderIds`;
- `minAge` e `maxAge` calibram a faixa etaria desejada das pessoas buscadas;
- `minAge = null` significa sem limite inferior explicito;
- `maxAge = null` significa sem limite superior explicito;
- `minAge = null` e `maxAge = null` significam nenhuma restricao etaria persistida.

Validacoes:

- `connectionTypeId`, quando informado, precisa existir;
- `desiredGenderIds`, quando informados, precisam existir;
- `maxMatchDistanceKm`, quando informado, deve ser maior que zero;
- `minAge`, quando informado, deve ser maior ou igual a `18`;
- `maxAge`, quando informado, deve ser maior ou igual a `18`;
- `minAge` nao pode ser maior que `maxAge`.

Observacao critica:

- este repositorio ainda nao possui um endpoint dedicado de descoberta/listagem de matches;
- hoje o backend persiste a preferencia etaria para que o consumidor do match use `users.birthdate` junto com `user_match_preferences.min_age` e `user_match_preferences.max_age`.

### `GET /users/me/profile/completion`

Objetivo:

- informar ao app em que etapa do onboarding o usuario esta.

Shape:

```json
{
  "profileCompleted": true,
  "matchPreferencesCompleted": false,
  "fullyCompleted": false,
  "missingProfileFields": [],
  "missingMatchPreferenceFields": ["genero desejado"]
}
```

Regra de perfil minimo:

- genero preenchido;
- ao menos uma forma de comunicacao;
- ao menos um estilo de vida;
- ao menos um interesse.

Regra de preferencia minima atual:

- distancia maxima do match preenchida;
- tipo de conexao preenchido;
- `lifestyleSimilarity` preenchido;
- ao menos um genero desejado.

Importante:

- `disabilities` pode ficar vazio e ainda assim o perfil minimo continua valido;
- `minAge` e `maxAge` sao opcionais no onboarding atual e nao entram no calculo de completude neste momento.

### `GET /users/me/profile/options`

Objetivo:

- entregar em uma unica chamada todos os lookups necessarios para formularios de perfil e preferencias.

Conteudo atual:

- `genders`
- `disabilities`
- `accessibilityNeeds`
- `autonomyLevels`
- `communicationForms`
- `lifestyleTypes`
- `energyLevels`
- `interestTypes`
- `connectionTypes`
- `similarityOptions`

Importante:

- nao existe lookup para faixa etaria, porque `minAge` e `maxAge` sao campos numericos simples.

## Semantica de edicao que o agente precisa respeitar

- nunca trate os `PUT` como merge parcial;
- se o cliente quer manter um item em uma lista, ele precisa reenviar esse item;
- lista vazia significa limpar a relacao;
- `null` em relacao singular opcional significa remover o valor;
- `GET` vazio nao significa erro, significa que a fatia ainda nao foi criada;
- salvar match preferences antes do perfil completo e permitido, mas isso nao torna o perfil minimo concluido automaticamente;
- trocar a localizacao sempre desativa coordenadas ativas anteriores;
- o onboarding so fica completo quando o endpoint de completion indicar `fullyCompleted = true`.

## Validacoes resumidas por endpoint

### Conta base

- `POST /auth/signup`
  - email obrigatorio e valido;
  - `birthdate` obrigatorio;
  - maioridade minima de `18` anos;
  - senha com tamanho minimo e complexidade;
  - email unico.

### Perfil

- `PUT /users/me/profile`
  - ids de lookup validos;
  - latitude e longitude em faixa valida;
  - latitude e longitude sempre juntas.

### Preferencias de match

- `PUT /users/me/match-preferences`
  - ids de lookup validos;
  - distancia positiva;
  - faixa etaria maior ou igual a `18`;
  - `minAge <= maxAge` quando ambos existem.

## Respostas de erro esperadas

- usuario autenticado nao encontrado nas rotas `/users/me*`
  - `404 USER_NOT_FOUND`
- erro de validacao em `PUT /users/me/profile` ou `PUT /users/me/match-preferences`
  - `400 VALIDATION_INVALID_FORMAT`
- usuario ja existente no signup
  - erro baseado em `USER_ALREADY_EXISTS`
- menor de idade no signup
  - erro baseado em `VALIDATION_UNDERAGE_USER`

## O que nao existe hoje

- nao existe endpoint de edicao direta de `name`, `lastName`, `email` ou `birthdate` depois do signup;
- nao existe endpoint dedicado para listar matches com filtros aplicados;
- nao existe `PATCH` para atualizacao parcial do perfil ou das preferencias.

Se qualquer uma dessas capacidades for adicionada, o agente deve documentar claramente a nova semantica para nao conflitar com os `PUT` atuais.

## Checklist de evolucao segura

1. Se adicionar novo campo obrigatorio ao onboarding, atualizar `getCompletionStatus` e este guia.
2. Se adicionar novo lookup, atualizar `import.sql`, `getProfileOptions` e os DTOs de options.
3. Se adicionar consumo real da faixa etaria no motor de match, usar `users.birthdate` como fonte de verdade da idade.
4. Se permitir atualizacao parcial, criar endpoints `PATCH` com semantica explicita.
5. Se criar endpoint de descoberta de match, documentar claramente como `minAge`, `maxAge` e `maxMatchDistanceKm` sao aplicados.
6. Se mover a responsabilidade de account edit para outro recurso, manter separado o que e conta base e o que e onboarding complementar.