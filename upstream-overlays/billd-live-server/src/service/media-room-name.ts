/**
 * Rooms reconciled against Revenue live sessions and PK matches.
 * Private 1v1 calls use `dm-` plus 32 hex characters and must not match,
 * or the live scanner will delete them.
 */
export const managedMediaRoom = /^(?:pk-)?[1-9][0-9]{0,19}$/;
