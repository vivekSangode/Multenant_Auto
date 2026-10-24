// Hash router: '#/companies/CMP_101' matches '/companies/:code'. Works on any static file server.

export function createRouter(routes, onRoute) {
  const compiled = routes.map((r) => ({
    ...r,
    regex: new RegExp(`^${r.path.replace(/:[a-zA-Z]+/g, '([^/]+)')}/?$`),
    keys: (r.path.match(/:[a-zA-Z]+/g) || []).map((k) => k.slice(1)),
  }));

  function resolve() {
    const [path, query = ''] = (window.location.hash.slice(1) || '/').split('?');
    for (const r of compiled) {
      const m = r.regex.exec(path);
      if (m) {
        const params = Object.fromEntries(r.keys.map((k, i) => [k, decodeURIComponent(m[i + 1])]));
        return onRoute(r, params, new URLSearchParams(query));
      }
    }
    return onRoute(compiled[0], {}, new URLSearchParams());
  }

  window.addEventListener('hashchange', resolve);
  return { start: resolve, go: (hash) => { if (window.location.hash === hash) resolve(); else window.location.hash = hash; } };
}
