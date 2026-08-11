# unify-api

Projeto Aplicado — Semestre 7 (PUC). API do Unify (Quarkus 3.32 / Java 25 / PostgreSQL).

## Setup local

1. **Variáveis de ambiente**
   ```bash
   cp .env.example .env
   # edite .env com as credenciais do seu Postgres local
   ```

2. **Chaves JWT** (não versionadas — gere as suas)
   ```bash
   mkdir -p secrets
   openssl genpkey -algorithm RSA -out secrets/privateKey.pem -pkeyopt rsa_keygen_bits:2048
   openssl rsa -pubout -in secrets/privateKey.pem -out secrets/publicKey.pem
   chmod 600 secrets/privateKey.pem
   ```

   As chaves **nunca** são empacotadas no JAR. A aplicação as lê dos caminhos
   definidos em `JWT_PRIVATE_KEY_LOCATION` / `JWT_PUBLIC_KEY_LOCATION`
   (default: `file:./secrets/privateKey.pem` e `file:./secrets/publicKey.pem`).
   Em contêiner, monte-as como secret/volume e aponte as variáveis para o caminho montado.

3. **Subir em dev**
   ```bash
   ./mvnw quarkus:dev
   ```
   Em `%dev` o schema é recriado a cada boot e os seeds de demonstração são aplicados.

4. **Swagger UI**: http://localhost:8080/q/swagger-ui

## Migrações

O schema é versionado com Flyway em `src/main/resources/db/migration`.
Em produção `quarkus.hibernate-orm.database.generation=validate` — o Hibernate
apenas valida; quem cria e altera tabelas é o Flyway.

> Toda alteração de entidade JPA exige uma migração nova em
> `src/main/resources/db/migration/`, nomeada `V<n>__descricao_curta.sql`.
> Migrações já aplicadas **nunca** são editadas. Em `%dev` o Hibernate ainda
> recria o schema, então **rode o perfil `prod` contra um banco limpo antes de
> abrir o PR** para garantir que a migração e as entidades estão em sincronia.

```bash
# validação do caminho de produção contra um banco vazio
createdb unify_prod_test
DB_NAME=unify_prod_test ./mvnw quarkus:run -Dquarkus.profile=prod
# esperado no log: "Successfully applied 2 migrations"
```

| Ambiente | `database.generation` | Flyway | Seed |
|---|---|---|---|
| `%dev` | `drop-and-create` | desligado | `import.sql` + `import-dev.sql` + `import-users.sql` |
| `%test` | `drop-and-create` | desligado | `import.sql` |
| default / prod | `validate` | `migrate-at-start=true` | apenas `V2__reference_data.sql` |

Os usuários de demonstração existem **somente** em `%dev`. O `DemoSeedGuard`
aborta o boot se dados de demonstração forem detectados fora de dev/test.

## Segurança

### Rotação de credenciais — PENDENTE (ação do responsável pelo projeto)

O `.env` nunca foi versionado, mas circulou fora do controle de versão (backups,
cópias de pasta, ambientes de agente). **As credenciais devem ser rotacionadas:**

1. **PostgreSQL** — trocar a senha do usuário da aplicação:
   ```sql
   ALTER USER unify WITH PASSWORD 'nova-senha-forte-gerada-aleatoriamente';
   ```
2. **Mailtrap** — invalidar as credenciais SMTP antigas no painel
   (Inboxes → SMTP Settings → Reset credentials) e gerar novas.
3. Atualizar o `.env` local de cada desenvolvedor com os novos valores.
4. Registrar a data da rotação aqui:

   | Item | Data da última rotação |
   |---|---|
   | Senha PostgreSQL | _pendente_ |
   | Credenciais Mailtrap | _pendente_ |
   | Par de chaves JWT | 2026-08-11 |

### CORS

A API é stateless (JWT no header `Authorization`). Não usamos cookies, portanto
`access-control-allow-credentials=false` e **nenhuma origem curinga é aceita** —
nem em `%dev`. Para usar o app em dispositivo físico via LAN, acrescente o IP a
`FRONTEND_ORIGINS` no `.env` em vez de reabrir o curinga.

### Rate limiting

Os 7 endpoints públicos de `/auth` são limitados por (IP + rota) via
`RateLimitFilter`: 10 requisições por janela de 60 s (configurável em
`unify.rate-limit.*`). Excedido o limite, a resposta é **429** com header
`Retry-After` e corpo `{"code":9004,"error":"TOO_MANY_REQUESTS",...}`.
O limite fica **desligado em `%test`** para não gerar flakiness.

### Limites de upload

`quarkus.http.limits.max-body-size=10M` no envelope HTTP e
`unify.upload.image.max-bytes=5242880` (5 MB) por imagem, validado na camada de
recurso antes de qualquer leitura para a heap. Excedido, a resposta é **413**
com `{"code":3009,"error":"VALIDATION_FILE_TOO_LARGE",...}`.

### Anti-enumeração de usuários

- `POST /auth/verify-email` — e-mail inexistente, conta já verificada e código
  errado retornam **exatamente a mesma resposta**
  (`USER_EMAIL_VERIFICATION_CODE_INVALID_OR_EXPIRED`, 400).
- `POST /auth/resend-email-verification` — responde **sempre 202** com mensagem
  genérica, independentemente de o e-mail existir.
- `POST /auth/forgot-password` — já respondia genericamente; mantido.

#### Risco aceito: `USER_EMAIL_NOT_VERIFIED` no signin

`POST /auth/signin` continua devolvendo `USER_EMAIL_NOT_VERIFIED` (403) quando a
conta existe mas não foi verificada, em vez de `AUTH_INVALID_CREDENTIALS` (401).
Isso **permite distinguir um e-mail cadastrado de um não cadastrado sem saber a
senha** — é uma enumeração de usuários conhecida.

**Decisão:** manter. O app precisa do sinal para redirecionar o usuário à tela
`/auth/email-code`; trocar por 401 genérico deixaria a conta não verificada sem
saída de UX. A mitigação é o rate limiting do `/auth/signin` (§2.9), que torna a
enumeração em massa inviável. Revisar se/quando o fluxo de verificação mudar.

## Contrato de erro

Toda resposta de erro segue o `ErrorResponse`:

```json
{ "code": 3010, "error": "VALIDATION_INVALID_ARGUMENT", "message": "...", "timestamp": 1760000000000 }
```

`code` é o **código de negócio** e nunca é usado como status HTTP. O status vem
sempre de `ErrorCode.getHttpStatus()`. `WebApplicationException` do JAX-RS
(404, 405, 415…) passa intacta pelo `GlobalExceptionMapper`.
