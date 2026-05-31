# plugin-prov-fe — Vue UI

Tool-level, i18n-only plugin (`service:prov:fe`), the Flexible Engine
provider for the `prov` service. Compiled to `webjars/prov-fe/vue/`.

The legacy `fe.js` was an empty `define({})`, parameter.csv is empty, and
the `prov` parent has no delegation hook, so this plugin ships only the
single `service:prov:fe:name` label. `requires: ['prov']`.

```bash
npm install && npm run build && npm run lint && npm test
```
