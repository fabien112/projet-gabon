import { reactive } from 'vue'

export const session = reactive({
  username: null,
  superAdmin: false,
})

export function applyMe(me) {
  session.username = me?.username || null
  session.superAdmin = !!me?.superAdmin
}

export function clearSession() {
  session.username = null
  session.superAdmin = false
}
