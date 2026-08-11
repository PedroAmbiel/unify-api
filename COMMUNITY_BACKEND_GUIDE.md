# Guia De Backend - Tela De Comunidade

Este documento descreve o contrato necessario para integrar a tela de Comunidade do app Unify com o backend.

A tela ja esta preparada no frontend para consumir dados reais. Ela nao deve exibir dados mockados. Todo conteudo visivel deve vir da API.

## Endpoint Necessario

Criar endpoint autenticado:

```http
GET /communities/feed
Authorization: Bearer <token>
```

Esse endpoint deve retornar os dados necessarios para renderizar:

- cabecalho da comunidade;
- quantidade de membros;
- descricao da comunidade;
- estado de participacao do usuario;
- lista de publicacoes;
- autores das publicacoes;
- imagens das publicacoes;
- contadores de curtidas e comentarios.

## Formato Da Resposta

```ts
{
  community: {
    id: string;
    name: string;
    memberCount?: number | null;
    description?: string | null;
    iconData?: string | null;
    isMember?: boolean | null;
  } | null;

  posts: {
    id: string;
    author: {
      id: string;
      name: string;
      avatarData?: string | null;
    };
    publishedAt?: string | null;
    body: string;
    mediaData?: string | null;
    likesCount?: number | null;
    commentsCount?: number | null;
    likedByCurrentUser?: boolean | null;
    commentedByCurrentUser?: boolean | null;
  }[];
}
```

## Regras Para O Backend

- O endpoint deve exigir autenticacao.
- `posts` deve sempre retornar um array, mesmo quando vazio.
- `community` pode retornar `null` caso nao exista comunidade disponivel para o usuario.
- Se `community` existir, `id` e `name` sao obrigatorios.
- Se existir post, `id`, `author`, `author.id`, `author.name` e `body` sao obrigatorios.
- Campos opcionais podem retornar `null`.
- O backend deve retornar apenas dados reais cadastrados no sistema.
- Nao retornar dados fake, placeholders ou exemplos.
- O frontend so renderiza imagens se as URLs forem enviadas.

## Comportamento No Frontend

A tela funciona assim:

- Se `community` vier como `null`, o frontend mostra estado vazio.
- Se `community` existir, o frontend mostra o cabecalho da comunidade.
- Se `posts` vier vazio, nenhum card de publicacao sera exibido.
- Se `iconData` vier vazio, o frontend usa apenas o icone padrao da comunidade.
- Se `avatarData` vier vazio, o frontend usa as iniciais do autor.
- Se `mediaData` vier vazio, o card aparece sem imagem.
- Contadores de curtidas e comentarios so aparecem quando enviados como numero.

## Exemplo De Resposta Com Dados

```json
{
  "community": {
    "id": "community-accessibility",
    "name": "Pioneiros da Acessibilidade",
    "memberCount": 12400,
    "description": "Compartilhando inovacoes e suporte para uma vida independente.",
    "iconData": null,
    "isMember": true
  },
  "posts": [
    {
      "id": "post-1",
      "author": {
        "id": "user-1",
        "name": "Mariana Costa",
        "avatarData": "https://api.exemplo.com/files/avatar-1.jpg"
      },
      "publishedAt": "ha 2 horas",
      "body": "Acabei de testar o novo prototipo de navegacao por voz no transporte publico. A precisao esta incrivel!",
      "mediaData": "https://api.exemplo.com/files/post-1.jpg",
      "likesCount": 42,
      "commentsCount": 12,
      "likedByCurrentUser": true,
      "commentedByCurrentUser": false
    }
  ]
}
```

## Exemplo De Resposta Sem Dados

```json
{
  "community": null,
  "posts": []
}
```

## Observacoes Sobre Imagens

As imagens serão recebidas via bitmap/byte, traduzidas e exibidas:

## Futuras Integracoes

A tela ja possui botao visual para criar publicacao, mas ele ainda nao tem acao conectada.

Crie tambem endpoints como:

```http
POST /communities/posts -> Cria uma nova publicacao em uma comunidade
POST /communities/posts/{postId}/likes -> Adiciona um like do usuario autenticado a um post
DELETE /communities/posts/{postId}/likes -> Deleta um like específico do usuário autenticado
GET /communities/posts/{postId}/comments -> Retorna os comentarios de um post específico
POST /communities/posts/{postId}/comments -> Cria um novo comentario em um post específico
```

Esses endpoints ainda nao sao obrigatorios para renderizar a tela atual.
