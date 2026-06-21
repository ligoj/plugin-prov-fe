/*
 * Plugin "prov-fe" — Flexible Engine implementation of plugin-prov.
 *
 * Tool-level, i18n-only plugin (`service:prov:fe`). The legacy `fe.js`
 * was an empty `define({})` and the `prov` parent has no delegation hook,
 * so this plugin's only contribution is the Flexible Engine i18n.
 */
import { useI18nStore } from '@ligoj/host'
import enMessages from './i18n/en.js'
import frMessages from './i18n/fr.js'
import service from './service.js'

const features = {}

export default {
  id: 'prov-fe',
  label: 'Flexible Engine',
  requires: ['prov'],
  install() {
    const i18n = useI18nStore()
    i18n.merge(enMessages, 'en')
    i18n.merge(frMessages, 'fr')
  },
  feature(action, ...args) {
    const fn = features[action]
    if (!fn) throw new Error(`Plugin "prov-fe" has no feature "${action}"`)
    return fn(...args)
  },
  service,
  meta: { icon: 'mdi-cloud-outline', color: 'red-darken-2' },
}

export { service }
