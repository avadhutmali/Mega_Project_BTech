# IdleGrid Student Client

The student-facing dashboard is a separate React/Vite app in this folder. Students can register from the sign-in screen, view live lab PC capacity, submit a capped compute job, see only their own jobs, and release an active job. Admin storage, bookings, DDP controls, and global job listings are not exposed here.

## Student accounts and history

Students can self-register from the sign-in screen. Optionally, preload accounts from environment variables on the Master before starting Spring Boot:

```bash
export IDLEGRID_CLIENT_USERS='student1:preload-a-long-password'
export IDLEGRID_CLIENT_MAX_USERS=50
```

On PowerShell, use `$env:IDLEGRID_CLIENT_USERS = 'student1:preload-a-long-password'`. The optional account list is comma-separated `username:password` pairs; commas are not supported inside usernames or passwords. Do not commit real passwords to the repository. With no preloaded accounts, students can still register through the client. `IDLEGRID_CLIENT_MAX_USERS` limits new registrations; its default is 50.

New accounts are saved in `users.json` under the configured `idlegrid.storage.directory` (default: `backend/storage/users.json`). Passwords are stored as salted PBKDF2 hashes, not plaintext. Sessions are random bearer tokens held in backend memory for 12 hours. The browser keeps its token in `sessionStorage`, so it is removed when that tab's session ends.

Job history, DDP run history, and storage-file ownership are persisted per account under the same storage directory. Active jobs or DDP runs are marked interrupted if the Master restarts; their saved history remains visible. Uploaded files are listed and downloaded only through the authenticated owner's client routes. The operator dashboard still has its all-files view.

The client has no extra per-student CPU/RAM ceilings or active-job count limit. Students can request resources like the operator dashboard; the scheduler places work only when lab capacity is available, and Agents apply the requested Docker CPU/RAM limits. The existing global operator APIs remain separate and unauthenticated on the trusted lab network.

## Run locally

Start the backend on port 9090, then:

```bash
cd client
npm install
npm run dev
```

Open `http://localhost:5174`. Vite proxies `/api` calls to `http://localhost:9090`. Set `VITE_MASTER_URL` to a different Master URL for another lab environment or a production build.

## Student API

The app calls the authenticated `/client/**` API only:

- `POST /client/auth/login`
- `POST /client/auth/register`
- `GET /client/auth/me`
- `POST /client/auth/logout`
- `GET /client/nodes`
- `GET /client/jobs`
- `POST /client/jobs`
- `POST /client/jobs/{id}/cancel`
- `GET /client/storage/files`
- `POST /client/storage/files`
- `GET /client/storage/files/{key}/content`
- `POST /client/training/runs`
- `GET /client/training/runs`
- `GET /client/training/runs/{id}/artifact`

Requests after login use a bearer session token. Job reads and cancellation are restricted to jobs owned by the signed-in account.

This is a basic prototype login for a trusted lab demo, not production identity management. Accounts have salted password hashes but there is no relational user database, TLS termination, registration verification, or rate limiting. Sessions are not durable. The older operator APIs remain unauthenticated, so this login does not make it safe to expose the Master to an untrusted network. Keep the Master on the controlled lab network and use HTTPS plus a proper identity provider before exposing it beyond the lab.
