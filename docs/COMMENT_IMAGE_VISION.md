# Descrição visual por IA de imagens de posts e comentários

Endpoints autenticados, `POST` sem corpo:

- `/communities/posts/{postId}/image-description` (post de comunidade)
- `/users/posts/{postId}/image-description` (post pessoal)
- `/communities/posts/{postId}/comments/{commentId}/image-description` (comentário)

Resposta: `{ "description": "Cachorro caramelo deitado em um sofá.", "source": "AI" | "SAVED" }`.

A descrição é gerada uma única vez: o resultado é salvo em `image_description` e as
próximas chamadas (de qualquer pessoa com acesso) devolvem o texto salvo com
`source: "SAVED"`, sem chamar o provedor. Uma descrição escrita pelo autor também é
devolvida como `SAVED`. Se a imagem do post for trocada durante a análise, o texto
não é salvo. Posts pessoais passam a devolver esse campo em `imageDescription`.

O texto é curto (alt text): até 200 caracteres pedidos no prompt, uma ou duas
frases, sem "A imagem mostra…", markdown ou listas. O servidor ainda normaliza a
resposta e corta em frases completas até 240 caracteres (limite da coluna).
No Gemini 3 o raciocínio fica em `GEMINI_THINKING_LEVEL=low` (o padrão do modelo é
`high`, mais lento e caro); `none` omite o campo.

O provedor padrão é Gemini. Crie uma chave na sua própria conta em [Google AI Studio](https://aistudio.google.com/apikey) e acrescente ao `.env` do backend:

```dotenv
IMAGE_VISION_PROVIDER=gemini
GEMINI_API_KEY=sua-chave
GEMINI_VISION_MODEL=gemini-3.1-flash-lite
```

Reinicie o processo da API após configurar. Não colocar a chave no frontend, em `EXPO_PUBLIC_*`, no chat ou no Git. Não substitua o `.env` inteiro: preserve suas configurações de banco e JWT.

O modelo padrão admite entrada de imagem e descrição em texto; o app lê esse texto com seu motor de voz existente. O plano gratuito depende da disponibilidade e dos limites da conta, não oferece uso ilimitado. Confira [preços](https://ai.google.dev/gemini-api/docs/pricing) e [limites](https://ai.google.dev/gemini-api/docs/rate-limits). No plano gratuito, o conteúdo pode ser usado pelo Google para melhorar produtos; use imagens de demonstração nos testes.

A integração usa [generateContent](https://ai.google.dev/api/generate-content), imagem JPEG em base64 e chave no header `x-goog-api-key`, sem expor a chave na URL. Respostas bloqueadas, vazias ou contendo apenas raciocínio não são reproduzidas como descrição; respostas truncadas só são aceitas até a última frase completa.

Para continuar usando a integração OpenAI existente, configure explicitamente `IMAGE_VISION_PROVIDER=openai`, `OPENAI_API_KEY` e, opcionalmente, `OPENAI_VISION_MODEL` (padrão `gpt-4.1-mini`). Não há troca automática de provedor após falha ou esgotamento de cota.

A falta de chave retorna 503 apenas neste recurso; não impede a inicialização da API. Configurações de servidor, banco, portas e CORS permanecem intactas. Não há dependência nova nem migração adicional para análise por IA (usa as colunas de V24/V25).

A imagem armazenada é carregada pelo serviço de mídia existente após autorização. A transação termina antes de enviá-la ao provedor selecionado. Não são enviados legendas, corpo do comentário, identificadores de conta ou JWT. Na integração OpenAI, `store: false` desativa o armazenamento da resposta. As políticas de retenção de cada provedor se aplicam ao processamento.

Limite de seis solicitações/minuto pelo filtro existente (quando habilitado), quatro análises concorrentes por instância e timeout externo de 45 segundos. Erros do provedor são sanitizados; 429 indica excesso/serviço ocupado, 502 falha externa. Respostas são `Cache-Control: no-store`.

Testes utilizam provedores simulados: verificam autorização antes de chamar IA, imagem binária no payload, ausência de chave, resposta, erros e recusas/incompletude. Para verificar sem credenciais reais: `./mvnw -Dtest=ImageVisionServiceTest,ImageDescriptionGenerationServiceTest,CommentImageDescriptionResourceTest test` (Java 25). `ImageDescriptionEndpointsIntegrationTest` cobre as rotas reais e o reaproveitamento (exige Postgres de teste).

Para validar no aparelho: entre em uma comunidade, publique um comentário com imagem e toque em “Ouvir descrição da imagem”. Confirme a leitura em português e o botão “Parar áudio”. Sem chave, só essa função informa indisponibilidade; a API continua iniciando normalmente. A validação em servidor com chave real e em aparelho permanece necessária.
