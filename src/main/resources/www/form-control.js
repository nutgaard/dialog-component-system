const actionMap = {
    'clear': () => {
        textarea.value = '';
    }
}

document.addEventListener('click', (e) => {
    if (e.target.tagName === 'BUTTON' && e.target.dataset.action) {
        actionMap[e.target.dataset.action]();
    }
});

const formMap = {
    'fetch_inbox': async () => {
        return fetch('/api/dialog')
    },
    'new_dialog': async () => {
        return fetch('/api/dialog', {
            method: 'POST'
        })
    },
    'new_message': async ({ dialog_id, content }) => {
        return fetch(`/api/dialog/${dialog_id}/message`, {
            method: 'POST',
            headers: {
                'Content-Type': 'text/plain'
            },
            body: content
        })
    },
    'add_component': async ({ entity_type, entity_id, component_type, component_meta}) => {
        const url = entity_type === 'dialog'
            ? `/api/dialog/${entity_id}/component`
            : `/api/message/${entity_id}/component`;
        const body = JSON.stringify({ type: component_type, ...JSON.parse(component_meta) });
        console.log('body', component_type);
        console.log('body', component_meta);
        console.log('body', JSON.parse(component_meta));
        console.log('body', body);
        return fetch(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: body
        })
    },
}
document.addEventListener('submit', async (e) => {
    e.preventDefault();
    console.log('submitting', e.target.name);
    const name = e.target.name;
    const data = Object.fromEntries(new FormData(e.target).entries());
    await formMap[name](data);
    console.log('Submit OK')
});