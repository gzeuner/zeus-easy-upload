(() => {
    const form = document.getElementById('connectionForm');
    const list = document.getElementById('connectionList');
    const status = document.getElementById('connectionStatus');
    const newButton = document.getElementById('newConnection');
    const testButton = document.getElementById('testConnection');
    const fields = {
        name: document.getElementById('connectionName'),
        type: document.getElementById('connectionType'),
        endpoint: document.getElementById('connectionEndpoint'),
        description: document.getElementById('connectionDescription'),
        username: document.getElementById('connectionUsername'),
        secret: document.getElementById('connectionSecret')
    };

    const showStatus = (message, kind = 'info') => {
        status.textContent = message;
        status.className = `alert alert-${kind}`;
    };

    const resetForm = () => {
        form.reset();
        fields.type.value = 'DB2_400';
        fields.secret.value = '';
    };

    const render = (profiles) => {
        list.replaceChildren();
        if (!profiles.length) {
            list.innerHTML = '<div class="text-secondary small">Noch keine Profile gespeichert.</div>';
            return;
        }
        profiles.forEach(profile => {
            const item = document.createElement('button');
            item.type = 'button';
            item.className = 'list-group-item list-group-item-action px-0 py-3';
            item.innerHTML = `<div class="d-flex justify-content-between gap-2"><strong></strong><span class="badge text-bg-light"></span></div><div class="small text-secondary text-truncate"></div>`;
            item.querySelector('strong').textContent = profile.name;
            item.querySelector('.badge').textContent = profile.credentialsConfigured ? 'Secret gesetzt' : 'Ohne Secret';
            item.querySelector('.text-secondary').textContent = profile.endpoint || '';
            item.addEventListener('click', () => loadProfile(profile.name));
            list.appendChild(item);
        });
    };

    const loadProfiles = async () => {
        const response = await fetch('/api/connections');
        if (!response.ok) throw new Error(`Laden fehlgeschlagen (${response.status})`);
        render(await response.json());
    };

    const loadProfile = async (name) => {
        const response = await fetch(`/api/connections/${encodeURIComponent(name)}`);
        if (!response.ok) { showStatus('Profil konnte nicht geladen werden.', 'danger'); return; }
        const profile = await response.json();
        fields.name.value = profile.name || '';
        fields.type.value = profile.type || 'DB2_400';
        fields.endpoint.value = profile.endpoint || '';
        fields.description.value = profile.description || '';
        fields.username.value = '';
        fields.secret.value = '';
        showStatus('Profil geladen. Ein leeres Secret lässt das bestehende Secret unverändert.', 'info');
    };

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        if (!form.reportValidity()) return;
        const credentials = {};
        if (fields.username.value.trim()) credentials.username = fields.username.value.trim();
        if (fields.secret.value) credentials.secret = fields.secret.value;
        try {
            const response = await fetch('/api/connections', {
                method: 'POST', headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({
                    name: fields.name.value.trim(), type: fields.type.value,
                    endpoint: fields.endpoint.value.trim(), description: fields.description.value.trim(), credentials
                })
            });
            if (!response.ok) throw new Error((await response.json().catch(() => ({}))).message || `Speichern fehlgeschlagen (${response.status})`);
            showStatus('Verbindung verschlüsselt gespeichert.', 'success');
            await loadProfiles();
        } catch (error) { showStatus(error.message, 'danger'); }
    });

    if (testButton) {
        testButton.addEventListener('click', async () => {
            const name = fields.name.value.trim();
            if (!name) {
                showStatus('Bitte zuerst ein gespeichertes Profil laden oder den Namen angeben.', 'warning');
                return;
            }
            showStatus('Verbindung wird getestet …', 'info');
            try {
                const response = await fetch(`/api/connections/${encodeURIComponent(name)}/test`, { method: 'POST' });
                const body = await response.json().catch(() => ({}));
                if (response.status === 404) {
                    showStatus('Profil nicht gefunden. Bitte zuerst speichern.', 'warning');
                    return;
                }
                if (!response.ok) {
                    throw new Error(body.message || `Test fehlgeschlagen (${response.status})`);
                }
                if (body.success) {
                    const product = body.databaseProductName ? ` (${body.databaseProductName})` : '';
                    showStatus(`OK: ${body.message}${product} — ${body.durationMs} ms`, 'success');
                } else {
                    showStatus(body.message || 'Verbindungstest fehlgeschlagen.', 'danger');
                }
            } catch (error) {
                showStatus(error.message, 'danger');
            }
        });
    }

    newButton.addEventListener('click', resetForm);
    loadProfiles().catch(error => showStatus(error.message, 'danger'));
})();
