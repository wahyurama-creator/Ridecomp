# RideComp Strava token server

A small Cloudflare Worker that holds RideComp's Strava **client secret**, so riders only tap
"Connect with Strava" (D-82). It answers three POST requests from the app and forwards each one to Strava:

| Path | Form field | Strava call |
|---|---|---|
| `/token` | `code` | `oauth/token` (authorization_code) |
| `/refresh` | `refresh_token` | `oauth/token` (refresh_token) |
| `/revoke` | `token` | `oauth/revoke` |

It stores nothing and logs nothing. Uploads go from the phone straight to Strava.

## Deploy (once, about 10 minutes)
1. **Strava app:** on strava.com/settings/api create the app *RideComp* (Category: Data Importer, Website:
   your site or `https://localhost`, **Authorization Callback Domain: `localhost`**). Note the Client ID and
   Client Secret.
2. **Worker:** on dash.cloudflare.com → Workers & Pages → Create → *Create Worker*, name it e.g.
   `ridecomp-strava`, Deploy, then *Edit code*: replace the code with `worker.js` and Deploy.
3. **Secrets:** Worker → Settings → Variables and Secrets → add two **Secrets** (type *Secret*, not Text):
   `STRAVA_CLIENT_ID` and `STRAVA_CLIENT_SECRET`. The secret is entered only here.
4. **App build:** in `~/.gradle/gradle.properties` (outside the repo) or the project `gradle.properties`:
   ```
   ridecomp.strava.clientId=<Client ID>
   ridecomp.strava.tokenServerUrl=https://ridecomp-strava.<your-subdomain>.workers.dev
   ```
   Both values are public (the Client ID is in every authorize URL). Rebuild the app.
5. **More riders:** a new Strava app accepts only its owner's account ("single player"). Before other
   riders can connect, request a higher athlete capacity from Strava (developers.strava.com → your app),
   which reviews the app against its brand guidelines ("Connect with Strava" button, "Powered by Strava").

## Test
`node --test server/strava-token-server/` (Node 18+, no dependencies; `fetch` to Strava is mocked).

## Security notes
- The secret exists only as a Worker secret; rotate it on strava.com and update the Worker if it leaks.
- Codes and tokens are checked against a short character pattern and size before reaching Strava.
- Replies carry only `access_token`, `refresh_token`, `expires_at` and the athlete's first and last name.
- Turn off Workers *Logs* / *Observability* request bodies if enabled (bodies contain tokens; the
  Worker itself never logs them).
