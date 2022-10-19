const ws = new WebSocket('ws://localhost:8080/notify');
ws.addEventListener('open', onWsOpen);
ws.addEventListener('close', onWsClose);
ws.addEventListener('error', onWsError);
ws.addEventListener('message', onWsMessage);

const textarea = document.querySelector('.inspection__log');
function append(message) {
    textarea.value = `${textarea.value}\n${message}`.trim();
}
function onWsOpen(e) {
    console.log('[WS] ', e)
    append('[WS] connected')
}
function onWsClose(e) {
    console.log('[WS] ', e)
    append('[WS] disconnected')
}
function onWsError(e) {
    console.error('[WS] ', e)
    append('[WS] error')
}
function onWsMessage(e) {
    console.log('[WS] ', e)
    append(`[WS] ${e.data}`)
}