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

5. **Push (opcional)** — ver [Notificações push](#notificações-push-firebase-cloud-messaging).
   Sem a conta de serviço do Firebase a API sobe normalmente; só o push fica
   desligado (as notificações in-app continuam).

## Notificações push (Firebase Cloud Messaging)

Semana 05. O backend envia push **direto ao FCM HTTP v1** (sem SDK do Firebase
e sem o serviço de push do Expo): `notification/push/FcmPushGateway` assina um
JWT com a conta de serviço, troca por um access token OAuth2 e chama
`POST https://fcm.googleapis.com/v1/projects/{projectId}/messages:send`.

1. Firebase Console → ⚙ **Configurações do projeto** → aba **Contas de serviço**
   → **Gerar nova chave privada** → confirmar. O navegador baixa um JSON.
2. Salve o arquivo como `secrets/firebase-service-account.json` (a pasta
   `secrets/` é ignorada pelo git). Outro caminho: `FIREBASE_SERVICE_ACCOUNT_FILE`.
3. Suba a API. O log deve mostrar `Push FCM ligado para o projeto Firebase '<id>'`.
   Sem o arquivo aparece `Push desligado: conta de serviço do Firebase não encontrada`.

> ⚠️ A conta de serviço é **segredo**: dá permissão de enviar push em nome do
> projeto. Nunca versione, nunca cole em issue/chat. Se vazar: Firebase →
> Contas de serviço → Gerenciar permissões (Google Cloud) → apagar a chave.
> O `google-services.json` do app **não** serve aqui (o backend recusa com uma
> mensagem explicando).

Como funciona:

| Peça | Papel |
|---|---|
| `NotificationService.emit*` | Grava a notificação na MESMA transação do evento (match, mensagem, follow, curtida, comentário). Nunca avisa a própria pessoa nem um par bloqueado; deduplica curtir/descurtir/curtir (`unify.notification.dedupe-window-hours`); agrupa mensagens não lidas da mesma conversa. |
| `PushNotificationDispatcher` | Observa o evento com `AFTER_SUCCESS` (rollback = sem push) e agenda numa virtual thread — a resposta HTTP não espera o FCM. |
| `PushDispatchService` | Lê a notificação + tokens numa transação curta, envia fora de transação e apaga tokens mortos (`UNREGISTERED`, `SENDER_ID_MISMATCH`, token inválido). |
| `FcmPushGateway` | Mensagem *data-only* de alta prioridade (`title`, `message`, `body` JSON, `channelId`, `tag`) — o formato que o `expo-notifications` desenha igual com o app aberto, em segundo plano ou fechado. |

Endpoints: `GET /notifications`, `GET /notifications/unread-count`,
`PUT /notifications/{id}/read`, `PUT /notifications/read-all`,
`POST /notifications/push-tokens` (`{token, platform}` → `{id}`),
`DELETE /notifications/push-tokens/{id}` e `GET /users/posts/{postId}` (post
único, destino das notificações de curtida/comentário).

Na suíte de testes o push fica desligado (`%test.unify.push.enabled=false`);
`FcmPushGatewayTest` sobe um servidor HTTP falso (token + `messages:send`) e
`PushDispatchServiceTest` troca o gateway por um stub (`QuarkusMock`).

## Administração e moderação (semana 06)

O backoffice web é o projeto irmão `unify-backoffice` (React + Vite,
`http://localhost:5173`). Ele usa esta mesma API, nas rotas `/admin/*`.

### Papel ADMIN

`users.role` (V27) aceita `USER` ou `ADMIN`. O JWT de admin sai com
`groups = ["user", "admin"]` (admin continua usando o app) e a claim `role`.
Toda rota `/admin/*` é `@RolesAllowed("admin")`: usuário comum recebe 403,
sem token 401.

**Não existe endpoint que mude papel.** Para promover uma conta já cadastrada:

```bash
# no boot (AdminBootstrapService), e-mails separados por vírgula
ADMIN_BOOTSTRAP_EMAILS="pessoa@exemplo.com" ./mvnw quarkus:dev

# ou direto no banco
psql -c "update users set role = 'ADMIN' where email = 'pessoa@exemplo.com';"
```

O papel entra no token no próximo login **ou** na próxima renovação
(`/auth/refresh` remonta os grupos a partir do banco).

Em `%dev`, `import-admin.sql` cria `admin@unify.dev` / `Admin@123`
(comente os inserts para subir sem admin). Essa conta é tratada como dado de
demonstração pelo `DemoSeedGuard`: fora de dev/test o boot aborta se ela existir.

### Banimento

`POST /admin/users/{id}/ban {"reason": "..."}` (10..500 caracteres) marca
`banned/banned_at/ban_reason` (V28, constraint `ck_users_ban_consistency`) e:

| Camada | Efeito |
|---|---|
| `/auth/signin` | 403 `USER_DISABLED` (4003) com o motivo — só depois de conferir a senha, para não revelar o ban a quem não tem a credencial |
| `/auth/refresh` | 403 `USER_DISABLED`, mesmo com refresh token emitido antes do ban |
| Sessões | todas revogadas na hora; tokens de push apagados |
| Access token já emitido | `BannedUserFilter` devolve 403 `USER_DISABLED` em qualquer rota autenticada |
| Visibilidade | some do discovery (inclusive convites), matches mútuos, perfil público (404, com galeria e áudio), seguidores/seguindo, feed, posts, comentários e membros de comunidade |
| Denúncias | as pendentes contra a conta viram `RESOLVED`; cada denunciante recebe **um** aviso `REPORT_UPDATE` |

Admin não bane admin nem a si mesmo (409). `POST /admin/users/{id}/unban`
limpa os três campos; as sessões antigas continuam revogadas.

### Rotas `/admin/*`

| Método | Rota | Descrição |
|---|---|---|
| GET | `/admin/lookups` | 12 catálogos com total e ativos |
| GET | `/admin/lookups/{tipo}?includeInactive=` | Itens com contagem de uso |
| POST | `/admin/lookups/{tipo}` | Cria (`{description, ionicIcon?}`); id vem de `seq_<tabela>_id` (V30) |
| PUT | `/admin/lookups/{tipo}/{id}` | Edita descrição/ícone |
| PUT | `/admin/lookups/{tipo}/{id}/active` | Ativa/desativa (`{active}`); inativo some das opções do app, continua válido em quem já usa |
| DELETE | `/admin/lookups/{tipo}/{id}` | Exclui só item sem uso; senão 409 sugerindo desativar |
| GET | `/admin/reports?status=&reportedUserId=&page=&size=` | Fila (`status`: ALL, PENDING, OPEN, UNDER_REVIEW, RESOLVED, DISMISSED) |
| GET | `/admin/reports/{id}` | Detalhe + ficha e histórico do denunciado |
| GET | `/admin/reports/{id}/post-media` | Imagem da publicação denunciada |
| PUT | `/admin/reports/{id}/status` | `{status, adminNote}`; encerrar exige nota (≥ 10) e avisa o denunciante; encerrada não reabre |
| GET | `/admin/users?query=&filter=&page=&size=` | Busca (`filter`: ALL, ACTIVE, BANNED, ADMIN) |
| GET | `/admin/users/{id}` | Ficha |
| POST | `/admin/users/{id}/ban` · `/unban` | Banimento |
| GET | `/admin/scoring-weights` · `/{grupo}` | Pesos de pontuação (`match`, `feed`, `community-discovery`) com padrão, limites, catálogo de origem e revisão |
| PUT | `/admin/scoring-weights/{grupo}` | `{expectedRevision, weights: {"MATCH_COMMUNICATION": 25, ...}}`; vale na hora (ver abaixo) |

"Minha conta" (qualquer usuário autenticado, usada pelo backoffice):

| Método | Rota | Descrição |
|---|---|---|
| PUT | `/users/me/account` | `{name, lastName, cellphone}`; celular só dígitos (10–13), vazio apaga, 409 se for de outra conta |
| PUT | `/users/me/account/password` | `{currentPassword, newPassword}`; senha atual errada é 400 `USER_CURRENT_PASSWORD_INVALID` (4008), não 401. Encerra todas as sessões e devolve tokens novos. Rate limit 5/min |

`{tipo}`: `genders`, `pronouns`, `disabilities`, `accessibility-needs`,
`autonomy-levels`, `communication-forms`, `lifestyle-types`, `love-languages`,
`energy-levels`, `interest-types`, `connection-types`, `community-categories`.
O gênero 4 ("Prefiro não informar") é protegido: o discovery o referencia
por id. `GET /users/me` devolve a identidade da conta (com `role` e `banned`).

Toda ação administrativa grava em `admin_audit_log` (V32) na mesma
transação: quem, o quê, alvo, detalhes e IP.

### Pesos de pontuação

Toda dimensão com peso — compatibilidade dos Encontros, ranking do feed da
aba Início e descoberta de comunidades — é uma constante de
`scoring/enums/ScoringDimension` (regra e valor padrão no código). O banco
guarda só o peso (`scoring_weights`, V33) e a revisão de cada grupo
(`scoring_group_revisions`).

- **Boot:** `ScoringWeightRegistry` lê as duas tabelas e mantém uma foto
  imutável em memória (array por ordinal da dimensão). Dimensão sem linha no
  banco usa o padrão.
- **Leitura:** discovery, feed e descoberta pegam a foto uma vez por
  requisição (`current()`): nenhuma consulta ao banco para saber peso.
- **Alteração:** o `PUT` trava a revisão do grupo, recusa com 409 quem editava
  uma revisão antiga, valida limites (até 2 casas decimais) e, nos Encontros,
  que as fatias em % somem exatamente 100. Grava, audita
  (`SCORING_WEIGHTS_UPDATE`) e troca a memória logo depois do commit.
- **Mais de uma instância:** cada réplica tem a própria memória; as outras só
  enxergam o peso novo ao reiniciar.

Gênero entra como filtro da busca e como penalidade (`MATCH_PENALTY_*`);
pronomes e deficiências não entram em nenhuma pontuação hoje.

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
| `%dev` | `drop-and-create` | desligado | `import.sql` + `import-dev.sql` + `import-users.sql` + `import-posts.sql` + `import-reports.sql` + `import-admin.sql` |
| `%test` | `drop-and-create` | desligado | `import.sql` |
| default / prod | `validate` | `migrate-at-start=true` | apenas `V2__reference_data.sql` (+ categorias da V4); nenhum admin semeado |

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
