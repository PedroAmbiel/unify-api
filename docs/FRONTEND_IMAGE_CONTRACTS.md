# Contratos de imagens e descrições usados pelo frontend

## Funcionalidades

- Conversas: o endpoint multipart existente `/chats/{conversationId}/messages/media`
  recebe `file`, `type` e `caption`. A descrição escrita no frontend é enviada
  na legenda e volta no campo `body`; não foi necessário alterar este contrato.
- Publicações pessoais: descrição integrada a `body` com marcador, inclusive
  ao editar; o limite combinado do frontend continua sendo 600 caracteres.
- Publicações de comunidades: `imageDescription` independente, na criação,
  edição JSON e troca/remoção de mídia; também devolvido no feed unificado.
- Comentários de comunidades: texto por JSON continua compatível; criação
  multipart aceita `body`, `image` e `imageDescription`. Permite enviar só imagem.
  A descrição tem até 240 caracteres e só é aceita com uma imagem.

## Comentários com imagens

`POST /communities/posts/{postId}/comments` aceita JSON ou multipart.
Na criação e listagem, o comentário devolve `mediaData` e `imageDescription`;
ambos são nulos em comentários sem imagem. A mídia é disponibilizada por
`GET /communities/posts/{postId}/comments/{commentId}/media`, com autenticação,
verificação do post e acesso à comunidade. Imagens são validadas, convertidas
para JPEG e armazenadas usando a infraestrutura de large objects existente.

A migração `V25__comment_images.sql` acrescenta as colunas à tabela
`post_comments` e libera a mídia na exclusão do comentário ou por cascata.
O backend mantém o limite de armazenamento de 2000 caracteres do texto do
comentário; o frontend usa um limite menor, de 400 caracteres.

## Conexão e execução

Esta implementação não altera arquivos de ambiente, URL de banco, credenciais,
URL da API, porta, host HTTP, CORS ou configurações de inicialização.
Também não executa migrations no banco existente nem reinicia o servidor.

Uma nova execução precisa carregar a versão atualizada do código e o esquema
compatível. Nos perfis com Flyway habilitado, usar o fluxo de migrations já
configurado, incluindo V22. Não executar o perfil dev ou test em um banco com
informações que devem ser preservadas: as configurações já existentes desses
perfis usam `drop-and-create`. Esta implementação não muda essas configurações.

## Verificação

A compilação e o empacotamento Quarkus foram validados sem iniciar o servidor.
Testes unitários cobrem recursos, validação de descrições, processamento de
imagem e regras de acesso à comunidade. Os testes foram executados em uma
cópia em `/tmp`, sem conexão ao banco do servidor existente.

A migração e os fluxos HTTP com persistência ainda precisam ser validados em
um ambiente de integração isolado. Testar câmera/galeria, reabertura do comentário,
falha de envio, imagem sem descrição e acesso negado a comunidades privadas.
