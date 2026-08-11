# Community Frontend Agent Guide

Este documento descreve o contrato atualizado do backend de comunidades depois da evolução para múltiplas comunidades criadas por usuários, com proprietário, cargos de membros e moderação.

## Resumo Rápido

- Comunidades agora são criadas por usuários autenticados.
- Cada comunidade possui um proprietário (`owner`) e esse proprietário também entra como `ADMIN` automaticamente.
- A participação em comunidade agora é vinculada ao `userProfile`, não apenas ao `user`.
- Cada membro pode ter um cargo: `ADMIN`, `MODERATOR` ou `MEMBER`.
- O feed continua retornando `community` + `posts`, mas agora deve ser carregado com `communityId` quando o frontend quiser abrir uma comunidade específica.
- O backend agora oferece listagem paginada e busca paginada de comunidades.
- Criação de comunidade e criação de post com imagem usam `multipart/form-data`.
- Ícone da comunidade, avatar do autor e mídia do post continuam sendo URLs protegidas que retornam `image/jpeg`.
- Moderadores e admins podem remover conteúdo de outros usuários dentro da comunidade em que possuem elevação.

## Modelo de dados útil para o frontend

### Community summary

As respostas de listagem, busca e feed usam esta estrutura de resumo:

```json
{
  "id": "uuid",
  "name": "Comunidade Unify",
  "memberCount": 12,
  "description": "texto",
  "iconData": "/communities/{communityId}/icon",
  "isMember": true,
  "owner": {
    "id": "uuid",
    "name": "Nome Sobrenome",
    "avatarData": "/communities/users/{userId}/avatar"
  },
  "currentUserRole": "ADMIN",
  "isOwner": true
}
```

Observações:

- `currentUserRole` pode vir `null` se o usuário autenticado ainda não participa da comunidade.
- `isOwner` facilita o frontend a decidir se deve exibir ações exclusivas do proprietário.
- `owner.avatarData` pode ser `null`.

## Endpoints principais

### Listar comunidades paginadas

`GET /communities?page=0&size=20`

Resposta:

```json
{
  "communities": [
    {
      "id": "uuid",
      "name": "Comunidade Unify",
      "memberCount": 12,
      "description": "texto",
      "iconData": "/communities/{communityId}/icon",
      "isMember": true,
      "owner": {
        "id": "uuid",
        "name": "Nome Sobrenome",
        "avatarData": "/communities/users/{userId}/avatar"
      },
      "currentUserRole": "ADMIN",
      "isOwner": true
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false
}
```

### Buscar comunidades paginadas

`GET /communities/search?query=acessibilidade&page=0&size=20`

Busca em `name` e `description`.

Observações:

- O parâmetro `query` é obrigatório.
- A busca é case-insensitive no backend.

### Criar comunidade

`POST /communities`

Content-Type:

`multipart/form-data`

Campos do form:

- `name`: obrigatório
- `description`: opcional
- `icon`: opcional

Resposta:

- Retorna o resumo da comunidade já com `currentUserRole = ADMIN` e `isOwner = true`.

Observações:

- O usuário precisa já ter um `userProfile` salvo para conseguir criar a comunidade.
- Se houver `icon`, ele é comprimido com Thumbnailator, convertido para JPEG e salvo como OID.

### Excluir comunidade

`DELETE /communities/{communityId}`

Observações:

- Somente o proprietário pode excluir a comunidade.
- O backend responde `204 No Content` em caso de sucesso.

## Feed da comunidade

### Endpoint

`GET /communities/feed?communityId={uuid}`

Resposta:

```json
{
  "community": {
    "id": "uuid",
    "name": "Comunidade Unify",
    "memberCount": 12,
    "description": "texto",
    "iconData": "/communities/{communityId}/icon",
    "isMember": true,
    "owner": {
      "id": "uuid",
      "name": "Nome Sobrenome",
      "avatarData": "/communities/users/{userId}/avatar"
    },
    "currentUserRole": "MODERATOR",
    "isOwner": false
  },
  "posts": [
    {
      "id": "uuid",
      "author": {
        "id": "uuid",
        "name": "Nome Sobrenome",
        "avatarData": "/communities/users/{userId}/avatar"
      },
      "publishedAt": "há 2 horas",
      "body": "texto",
      "mediaData": "/communities/posts/{postId}/media",
      "likesCount": 42,
      "commentsCount": 3,
      "likedByCurrentUser": true,
      "commentedByCurrentUser": false
    }
  ]
}
```

Observações:

- `communityId` deve ser enviado quando a UI estiver abrindo uma comunidade específica.
- Se `communityId` não for informado, o backend ainda tenta resolver uma comunidade padrão/featured para retrocompatibilidade.
- `publishedAt` já vem humanizado em português.
- `posts` sempre vem como array.
- `iconData`, `avatarData` e `mediaData` podem vir `null`.

## Participação na comunidade

### Entrar na comunidade

`POST /communities/membership?communityId={uuid}`

Resposta:

```json
{
  "communityId": "uuid",
  "isMember": true,
  "memberCount": 13,
  "role": "MEMBER",
  "isOwner": false
}
```

### Sair da comunidade

`DELETE /communities/membership?communityId={uuid}`

Retorna a mesma estrutura acima, com `isMember = false` e `role = null`.

Observações:

- O usuário precisa já ter um `userProfile` salvo para conseguir entrar na comunidade.
- O proprietário não pode sair da própria comunidade enquanto ela existir.
- Criar post, curtir, descurtir e comentar exige participação.
- Se o usuário tentar interagir sem participar, o backend responde `409 RESOURCE_CONFLICT`.

## Listagem de membros

### Listar membros atuais da comunidade

`GET /communities/{communityId}/members`

Resposta:

```json
{
  "communityId": "uuid",
  "members": [
    {
      "userProfileId": "uuid",
      "name": "Nome Sobrenome",
      "avatarData": "/communities/users/{userId}/avatar",
      "role": "ADMIN"
    }
  ]
}
```

Observações:

- Esta rota retorna somente o cabeçalho do perfil de cada membro atual da comunidade.
- `userProfileId` é o identificador que o frontend deve usar para abrir `GET /users/me/profile/public?userProfileId=...`.
- `role` pode ser `ADMIN`, `MODERATOR` ou `MEMBER` e reflete o cargo atual do membro na comunidade.
- `avatarData` continua sendo uma URL protegida e pode vir `null`.
- Todo membro retornado por esta rota já possui `userProfile`, porque a membership agora referencia `fk_user_profile`.

## Gestão de cargos

### Atualizar cargo de um membro

`PUT /communities/{communityId}/members/{userProfileId}/role`

Body JSON:

```json
{
  "role": "MODERATOR"
}
```

Resposta:

```json
{
  "communityId": "uuid",
  "user": {
    "userProfileId": "uuid",
    "name": "Nome Sobrenome",
    "avatarData": "/communities/users/{userId}/avatar"
  },
  "role": "MODERATOR",
  "owner": false
}
```

Regras importantes do backend:

- `ADMIN` e `MODERATOR` podem alterar a elevação de outro membro.
- O usuário não pode alterar o próprio cargo.
- O proprietário não pode ter o cargo alterado.
- Um `MODERATOR` não pode promover alguém para `ADMIN`.
- Um `MODERATOR` também não pode alterar outro `ADMIN`.

## Criar publicação

### Endpoint

`POST /communities/posts?communityId={uuid}`

### Content-Type

`multipart/form-data`

### Campos do form

- `body`: obrigatório
- `image`: opcional

### Resposta

- Retorna a publicação criada no mesmo formato dos itens de `posts` do feed.

Observações:

- A imagem é comprimida com Thumbnailator, convertida para JPEG e armazenada como OID.
- Se não houver imagem, `mediaData` vem `null`.

### Excluir publicação

`DELETE /communities/posts/{postId}`

Regras:

- O autor pode excluir a própria publicação.
- `ADMIN` e `MODERATOR` podem excluir publicação de outro usuário se estiverem elevados naquela mesma comunidade.
- Retorno de sucesso: `204 No Content`.

## Curtidas

### Curtir publicação

`POST /communities/posts/{postId}/likes`

### Remover a própria curtida

`DELETE /communities/posts/{postId}/likes`

### Remover curtida de outro usuário por moderação

`DELETE /communities/posts/{postId}/likes/{userId}`

Resposta dos endpoints de curtida/remoção:

```json
{
  "postId": "uuid",
  "likesCount": 10,
  "likedByCurrentUser": true
}
```

Regras:

- O usuário autenticado pode remover a própria curtida.
- `ADMIN` e `MODERATOR` podem remover curtida de outro membro dentro da comunidade onde estão elevados.

## Comentários

### Listar comentários

`GET /communities/posts/{postId}/comments`

Resposta:

```json
{
  "postId": "uuid",
  "comments": [
    {
      "id": "uuid",
      "author": {
        "id": "uuid",
        "name": "Nome Sobrenome",
        "avatarData": "/communities/users/{userId}/avatar"
      },
      "publishedAt": "há 3 minutos",
      "body": "texto",
      "commentedByCurrentUser": false
    }
  ]
}
```

### Criar comentário

`POST /communities/posts/{postId}/comments`

Body JSON:

```json
{
  "body": "Meu comentário"
}
```

Retorna o comentário criado.

### Excluir comentário

`DELETE /communities/posts/{postId}/comments/{commentId}`

Regras:

- O autor pode excluir o próprio comentário.
- `ADMIN` e `MODERATOR` podem excluir comentário de outro usuário dentro da comunidade em que estão elevados.
- Retorno de sucesso: `204 No Content`.

## Endpoints de imagem

Todos exigem autenticação e retornam `image/jpeg`.

- `GET /communities/{communityId}/icon`
- `GET /communities/posts/{postId}/media`
- `GET /communities/users/{userId}/avatar`

## Adequação importante no frontend

Os campos `iconData`, `avatarData` e `mediaData` continuam sendo URLs protegidas, não base64 embutido.

O frontend precisa:

1. Tratar esses campos como URL autenticada.
2. Fazer fetch com Bearer token.
3. Converter o blob/arraybuffer em URL local para renderização.

## Fluxo recomendado para a nova experiência

1. Abrir a tela de exploração chamando `GET /communities?page=0&size=...`.
2. Usar `GET /communities/search?...` para a busca textual.
3. Ao selecionar uma comunidade, chamar `GET /communities/feed?communityId=...`.
4. Se `isMember = false`, validar primeiro se o usuário já possui `userProfile`; se não possuir, redirecionar para o fluxo de completar/criar perfil antes do join.
5. Se o usuário for `ADMIN` ou `MODERATOR`, habilitar UI de moderação.
6. Se `isOwner = true`, habilitar UI de exclusão da comunidade.
7. Para criar comunidade, garantir também que o usuário já tenha `userProfile`; a API responde conflito se tentar criar sem perfil.
8. Para criar comunidade ou publicação com imagem, sempre usar `multipart/form-data`.
9. Para gestão de cargos, chamar `PUT /communities/{communityId}/members/{userProfileId}/role` usando o `userProfileId` recebido em `GET /communities/{communityId}/members`.
10. Para moderação de conteúdo, usar os endpoints `DELETE` específicos de posts, likes e comentários.

## Tratamento de erro esperado

- `400 VALIDATION_INVALID_FORMAT`: paginação inválida, busca vazia, nome/corpo ausente ou payload inválido.
- `403 AUTH_FORBIDDEN`: tentativa de excluir comunidade sem ser owner, alterar cargo sem permissão ou moderar conteúdo sem elevação suficiente.
- `404 RESOURCE_NOT_FOUND`: comunidade, membro, post, curtida, comentário, avatar, mídia ou ícone não encontrado.
- `409 RESOURCE_CONFLICT`: usuário tentou entrar/criar comunidade sem ter `userProfile`, tentou interagir sem participar da comunidade ou tentou sair sendo o proprietário.

## Observação final

O backend continua reutilizando a mesma estratégia de compressão e armazenamento OID já usada nas imagens de perfil, agora também aplicada à criação de comunidades e posts com imagem.