# Descrição visual por IA de imagens nos comentários

Endpoint autenticado: `POST /communities/posts/{postId}/comments/{commentId}/image-description`, sem corpo.
Resposta: `{ "description": "Conteúdo visual descrito em português", "source": "AI" }`.

O provedor padrão é Gemini. Crie uma chave na sua própria conta em [Google AI Studio](https://aistudio.google.com/apikey) e acrescente ao `.env` do backend:

```dotenv
IMAGE_VISION_PROVIDER=gemini
GEMINI_API_KEY=sua-chave
GEMINI_VISION_MODEL=gemini-3.1-flash-lite
```

Reinicie o processo da API após configurar. Não colocar a chave no frontend, em `EXPO_PUBLIC_*`, no chat ou no Git. Não substitua o `.env` inteiro: preserve suas configurações de banco e JWT.

O modelo padrão admite entrada de imagem e descrição em texto; o app lê esse texto com seu motor de voz existente. O plano gratuito depende da disponibilidade e dos limites da conta, não oferece uso ilimitado. Confira [preços](https://ai.google.dev/gemini-api/docs/pricing) e [limites](https://ai.google.dev/gemini-api/docs/rate-limits). No plano gratuito, o conteúdo pode ser usado pelo Google para melhorar produtos; use imagens de demonstração nos testes.

A integração usa [generateContent](https://ai.google.dev/api/generate-content), imagem JPEG em base64 e chave no header `x-goog-api-key`, sem expor a chave na URL. Respostas bloqueadas, truncadas, vazias ou contendo apenas raciocínio não são reproduzidas como descrição.

Para continuar usando a integração OpenAI existente, configure explicitamente `IMAGE_VISION_PROVIDER=openai`, `OPENAI_API_KEY` e, opcionalmente, `OPENAI_VISION_MODEL` (padrão `gpt-4.1-mini`). Não há troca automática de provedor após falha ou esgotamento de cota.

A falta de chave retorna 503 apenas neste recurso; não impede a inicialização da API. Configurações de servidor, banco, portas e CORS permanecem intactas. Não há dependência nova nem migração adicional para análise por IA.

A imagem armazenada é carregada pelo serviço de mídia existente após autorização. A transação termina antes de enviá-la ao provedor selecionado. Não são enviados legendas, corpo do comentário, identificadores de conta ou JWT. Na integração OpenAI, `store: false` desativa o armazenamento da resposta. As políticas de retenção de cada provedor se aplicam ao processamento.

Limite de seis solicitações/minuto pelo filtro existente (quando habilitado), quatro análises concorrentes por instância e timeout externo de 45 segundos. Erros do provedor são sanitizados; 429 indica excesso/serviço ocupado, 502 falha externa. Respostas são `Cache-Control: no-store`. Não há persistência da descrição gerada no banco.

Testes utilizam provedores simulados: verificam autorização antes de chamar IA, imagem binária no payload, ausência de chave, resposta, erros e recusas/incompletude. Para verificar sem credenciais reais: `JAVA_HOME=/home/mayara/.jdks/temurin-25.0.3 bash mvnw -Dtest=ImageVisionServiceTest,CommentImageDescriptionResourceTest test`.

Para validar no aparelho: entre em uma comunidade, publique um comentário com imagem e toque em “Ouvir descrição da imagem”. Confirme a leitura em português e o botão “Parar áudio”. Sem chave, só essa função informa indisponibilidade; a API continua iniciando normalmente. A validação em servidor com chave real e em aparelho permanece necessária.
