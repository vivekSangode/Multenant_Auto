// Where the portals find the backend. Keycloak's URL and realm names come from the control plane
// (GET /api/v1/public/config), so only the API base URLs live here.
export const CONFIG = {
  controlPlane: 'http://localhost:8080',
  services: {
    vehicle: 'http://localhost:8091',
    inventory: 'http://localhost:8092',
    booking: 'http://localhost:8093',
  },
};
