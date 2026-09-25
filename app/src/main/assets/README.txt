Kimi API —— 本地 OpenAI 兼容网关

接口：
  GET  /v1/models
  POST /v1/chat/completions   （stream=true 时以 SSE 流式返回）

鉴权：
  Header: Authorization: Bearer <设置页里的 API Key>

账号：
  在「账号」页添加 Kimi refresh_token（有效期约 3 个月），
  程序会自动刷新 access_token 并在多账号间轮询。

提示：
  把 Base URL（http://手机IP:9980/v1）与 API Key 填入任意 OpenAI 客户端即可。
