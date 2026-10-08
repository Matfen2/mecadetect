import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';

import { routes } from './app.routes';
import { BASE_PATH } from './core/api';
import { authInterceptor } from './core/auth/auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // withComponentInputBinding : les paramètres de route et les données
    // de route arrivent directement dans les input() des composants
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor])),
    // URL relative : en développement, le proxy Angular redirige /api vers Spring Boot ;
    // en production, Nginx fera la même chose. Aucun souci de CORS.
    { provide: BASE_PATH, useValue: '/api/v1' },
  ],
};
