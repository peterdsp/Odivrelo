# Poravia API

This directory will contain the public read API and private administrative
review service.

The public API reads a compiled, rights-cleared, read-only database. It does not
read candidate or permission-pending records directly.

The administrative surface requires authentication and should be isolated from
public routes in deployment.
