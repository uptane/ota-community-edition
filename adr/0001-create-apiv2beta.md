# Create api/v2beta

## Status

Draft, Proposed

## Context

`tuf-reposerver` exposes the complexity of [TUF](https://github.com/theupdateframework/specification/) to the user by allowing direct manipulation and visibility of TUF JSON metadata by the user. This design choice was made so the user has full control over the contents TUF metadata. To query for available packages, the user needs to parse targets.json and possibly follow delegations to find delegated packages.

Even though this gives the user maximum flexibility, this has been shown to be problematic, as users do not want to learn about TUF, Uptane or json metadata and simply want to use our system to securely manage updates. Error are difficult to understand for the user, and in general using the system is not easy without understanding the implications of TUF metadata.

We want to provide the user a way to use TUF, but not having to manage TUF metadata explicitly, or even know about this metadata. A user should be able to create and query packages and delegations without having to manage TUF metadata. If the user does not have any explicit delegations, then they should also not need to know what a delegation is or how it works, but still be able to install packages present in trusted delegations.

`uptane-sign` uses the same APIs to manage packages and uptane metadata.

## Decision

We will add a new api prefix, `api/v2beta` where new APIs will be provided allowing an user to manage their repository, providing CRUD (Create, Read, Update, Delete) capabilities, without explicitly managing JSON metadata.

`tuf-reposerver` will implicitly provide the available packages to the device, in the existing endpoints, in the form of TUF JSON metadata.

Endpoints to provide:

All endpoints below will be prefixed by `/<reposerver host>/api/v2beta/user_repo/`.

POST `/` create a new repository

PUT `/root/rotate` Rotate a root.json to a new version, with new root and targets keys

POST `/root` with an offline signed root.json json body. Rotate root.json to a new root.json. Validations are executed to ensure the new root.json is valid.

DELETE `/root/private_keys/:key-id` Delete the private key idenfified by `key-id` from the database

GET ``/api/v1/user\_repo/root/private_keys/:key-id` Return the private key identified by `key-id` from the database

GET `/:version.root.json` Return the root.json with :version

GET `/targets.json`
GET `snapshot.json`
GET `timestamp.json` Return tuf json metadata

`delegated-role` is ultimately represented by a new delegated role under the json paths `.signed.delegations.keys` and `.signed.delegations.roles` of a repository `targets.json`, however, a user does not need to know this to operate on delegations.

PUT `/delegated-roles` With a list of delegated roles as body
GET `/delegated-roles` Returns list of existing delegated roles
DELETE `/delegated-roles/:deleted-role-name` Remove specified role from delegated roles
PUT `/delegated-roles/keys` Add list of keys to the allowed delegation key
GET `/delegated-roles/keys` Returns list of allowed delegation keys
GET `/delegated-roles/info` Returns information about all delegated roles. This will include information about both remote and normal delegations.

`delegated-metadata` and `remote-delegated-metadata` are ultimately represented by a `SignedPayload[TargetsRole]` accessible under by the device under the path `/delegated-metadata/:role-path`

PUT `/delegated-metadata/:role-path` Store a new delegated metadata entity. The body must be in the same format as `targets.json`.
GET `/delegated-metadata/:role-path` Return the stored metadata.

PUT `/remote-delegated-metadata/:role-path` Add a new remote delegated role
PUT `/remote-delegated-metadata/:role-path/refresh` Refresh contents of delegated roles from remote

GET `/delegated-metadata/targets` Returns a paginated list of all targets. This needs to be prefetched from the delegations metadata, remotely if needed. This process must be transparent to the user.

PUT `/uploads/:target-filename` Allows a user to upload a binary than can later be accessed by the device using `/targets/:target-filename`. This endpoint will redirect the user to a URI that can be used to upload the binary to, usually a pre signed S3 URL.

POST `/multipart/target-filename/initiate`
GET `/multipart/:target-filename/url`
PUT `/multipart/:target-filename/complete` Urls to manage multi part upload.

PUT `/targets/expire/not-before` Change targets.json expiration date

POST `/targets/:target-filename` Add a new target using metadata only, no binary is uploaded. The binary will need to be uploaded using other endpoint. `uploads`, or `multipart`, or `PUT` on this endpoint.
PUT `/targets/:target-filename` Upload a new binary for `:target-filename`
HEAD `/targets/:target-filename` Returns 2xx or 404 depending on whether the target binary exists or not.
GET `/targets/:target-filename` Returns the binary target. Might return a redirect to the location of the binary.
DELETE `/targets/:target-filename` Delete a target and its binary
PATCH `/targets/:target-filename` Edit a target metadata. Custom fields can also be edited using this endpoint

PUT `/targets.json` Upload an offline signed `targets.json`

GET `/target-items` Get filtered, paginated list of target items
GET `/target-items/:target-filename` Get the target-item resourcewwwww
PUT `/target-items/:target-filename/comments` Add a comment to a target-item
GET `/target-items/:target-filename/comments` Get all comments for a target-item
    
GET `/comments` Get paginated list of comments. Support filtering by target name.

`uptane-sign` will be updated to use the new APIs, but favour operations that manipulate tuf metadata directly and use the existing API endpoints.

All slashes in filenames need to be escaped to `%2f`. Not having this requirement is what prevents the current api from properly nesting resources, for example, we currently cannot implement `/target-items/:target-filename/something` because we cannot determine if `/something` is part of the target filename or the API endpoint URI.

## Consequences

Building an UI client, or otherwise any API client, will be simpler and more ergonomic as simple resources can be exposed that will later be translated to TUF JSON metadata.

Fully featured endpoints to directly manage TUF json metadata, including moving keys offline, will still be provided, for users that which to explicitly manage their metadata. `tuf-reposerver` will need to determine if the user is using offline signed metadata and not manage targets.json implicitly.

A custom metadata JSON blob can be still uploaded as part of target item, but is treated as just another attribute of a target item resource. Custom attributes required in all target items, like `name` and `version` will be managed by tuf-server as normal attributes of a target item, but included in the custom metadata attribute in targets.json. This process will be transparent for the user.

To help with migration and so that existing UI and API clients can still use `tuf-reposerver`, the old API will remain available. However, new features will only be implemented using the new `v2beta` api.

For the device, there are no consequences and the repository should be exposed as usual via TUF JSON metadata.

The following endpoints, will not be moved to `api/v2beta`:

All endpoints under `trusted-delegations`. The functionality is provided with resources under v2beta.
 
The GET endpoint on `delegations/:role-path` will still be accessible to keep backwards compability, however, all other endpoints under `delegations` are replaced by the `delegated-metadata` and `remote-delegated-metadata` resources.

All endpoints under `delegation_items`.

`/multipart/initiate/:target-filename`, `/multipart/url/:target-filename`, `/multipart/url/:target-filename/complete` will be replaced with endpoints under `/multipart` defined above.

`/proprietary-custom/:target-filename` will be replaced by `PATCH /target-items/:target-filename`

`POST /targets`, will be replaced by `/targets.json` to upload an offline signed `targets.json`.

Endpoints under `/comments`, will be replaced by corresponding new endpoints under `target-items`.
