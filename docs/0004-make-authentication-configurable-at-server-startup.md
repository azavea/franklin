---
id: 0004-make-authentication-configurable-at-server-startup
title: 4 - Configurable Authentication at Server Startup
---

Date: 2020-06-29

Status
-----

Accepted

Context
-----

We would like to add some authentication support to Franklin, but are unsure exactly what the shape of this feature should be.
Most of Azavea's authentication mechanisms include JWT of some form - GroundWork and RF use JWTs from Auth0. Additionally, we have
a few clients who have expressed interest in Franklin that also use JWTs. That said, there are a few cases where opaque token
authentication might be appropriate. As a result, how to perform authentication should probably be configurable via the command line
and/or environment variables.

In addition to what kind of authentication to do, we'd also like to be able to configure the "level" of authentication. There are at
least three scenarios we should accomodate:

- All endpoints require authentication
- Destructive endpoints (e.g. POST, PUT, DELETE) require authentication, but GET endpoints do not
- No endpoints require authentication

This ADR exists to propose strategies for addressing those two challenges. I created
[a toy repository](https://github.com/jisantuc/configable-auth) to explore our options based on our
[Http4s project template](https://github.com/azavea/azavea.g8).

### Configuring levels

Treat authentication as some function from `String => IO[Either[Err, UserInfo]]`. We don't need to know the particular shapes of
`Err` and `UserInfo` to use this formulation to think about how to configure which endpoints are authenticated. If we're
capable of imagining some kind of empty or default `UserInfo`, then we can easily come up with a function

```scala
def successful(token: String): IO[Either[Err, UserInfo]] = IO.pure(Right(UserInfo.default))
```

Separately, we if _want_ to apply authentication, then we have to verify the token:

```scala
def validate(token: String): IO[Either[Err, UserInfo]] = IO { ??? }
```

I've chosen these particular types because they align well with libraries we're already using. For instance, if our
token parameter is the first endpoint input, like it is
[in Granary](https://github.com/raster-foundry/granary/blob/master/api/src/main/scala/com/rasterfoundry/granary/endpoints/TaskEndpoints.scala#L16-L17),
we can
[use `tapir`'s `serverLogicPart` function](https://github.com/raster-foundry/granary/blob/master/api/src/main/scala/com/rasterfoundry/granary/services/TaskService.scala#L66)
to apply one of these functions and keep the routes' business logic distinct from authentication concerns.

The question at that point is how to specify which endpoints or groups of endpoints require authentication. It was sufficiently simple to create some
algebraic data type called `Domain` and choose from among them from the command line at server startup. I don't believe this question deserves much further
consideration. An example of using `serverLogicPart` and a command line flag for controlling which endpoints require authentication can be seen in
[this PR](https://github.com/jisantuc/configable-auth/pull/1) against the toy repository.

### Configuring authentication strategy

This choice is more difficult, because the strategies themselves are more difficult.
I considered three strategies for making the authentication strategy a configurable
option at server startup:

- dynamic class loading
- providing an endpoint that returns `UserInfo` or an error
- we implement a few strategies and hope we did enough

To attempt to come up with a fair evaluation, I considered difficulty along several axes:

- how much harder is it to set up Franklin?
- how much harder is it to deploy Franklin?
- what restrictions are placed on the user (someone deploying their own Franklin instance) as a result of this choice?
- what restrictions are placed on us as a result of this choice?
- how helpful can we expect to be when something goes wrong?

#### Dynamic class loading

In this option, a user provides a path to be added to the classpath and a class name that has access to some function matching the signature above.

##### How much harder is it to set up Franklin?

If a user doesn't want to do authentication, not much -- we can reasonably expect ourselves to include a `no-auth` implementation in the repository.
If a user wants to write an authentication strategy, it will be more difficult. We will probably provide some sensible defaults (e.g. Auth0 JWT authentication),
but custom authenticators will require custom code that runs on the JVM. This strategy is outside of my strikezone even after some experimentation, but in my experiment, I didn't figure out
how to provide a class at runtime that I could cast to what I wanted and call methods from. You can see that experiment on a branch in the [toy repo](https://github.com/jisantuc/configable-auth/tree/dynamic-classloading/dynamic-test-code). It's not obvious to me if someone who's more used to Java tooling and lower-level programming in Java would think this is challenging.

##### How much harder is it to deploy Franklin?

I don't think this option would make it significantly harder to deploy Franklin. You
need to be able to place a jar somewhere that the Franklin container you intend
to run can access it. I think "Container image A, but with a file in location B" is a four line Dockerfile.

##### What restrictions are placed on the user as a result of this choice?

Users who want custom authentication logic _must_ write that logic in a language that runs on the JVM.

##### What restrictions are placed on us as a result of this choice?

The biggest restriction is that we'd be some pretty serious jerks if we rewrote Franklin
in anything that doesn't run on the JVM in the future.

##### How helpful can we expect to be when something goes wrong?

That depends on the specific implementation that someone has chosen. 

If someone writes their authentication implementation in Scala or Java, I think we'd probably not have much trouble
helping them debug problems. If we end up in a rabbit hole trying to figure out
how `erjang` works, I think we might be in trouble. A lot of languages will run on the JVM if you really want them to, and we'll never have 100% coverage.

#### Providing an endpoint that returns UserInfo or an error

In this option, a user provides an absolute URL that accepts requests containing a token to validate and returns either `UserInfo` or an error.
An example of what might be required for this strategy can be seen in [`jisantuc/configable-auth#2`](https://github.com/jisantuc/configable-auth/pull/2).

##### How much harder is it to set up Franklin?

In the no-authentication case, it is identically easy to set up Franklin, since we can fall back to the default without actually making a request. If a user wants authentication to occur, they'll need to ensure:

- that their authentication service is running
- that Franklin can see it from where they're running Franklin
- that their authentication service returns data of the correct shape

These requirements collectively increase the difficulty of starting Franklin for the first time, especially if the existing authentication service can't be easily modified.

##### How much harder is it to deploy Franklin?

Deploying Franklin requires similar criteria to starting Franklin. The user must ensure the same three things. Depending on their deployment scenario, this
may be easy because the requirements match, or it may be difficult. If we encourage users to run containers alongside Franklin to meet this requirement locally,
that should facilitate an easier deployment story.

##### What restrictions are placed on the user as a result of this choice?

Users are free to use whatever language they choose to implement their authentication
service, but the response from the endpoint provided must match a specific shape.

##### What restrictions are placed on us as a result of this choice?

The main restriction placed on us is that this would introduce a cost to increasing authentication complexity. For example, in Raster Foundry, we added scopes to users. If we were to add scopes to Franklin's `UserInfo`, we'd need to ensure that
we thought through what sensible default behavior should look like, so that downstream
users wouldn't need to figure out scopes or possibly deploy changes to their authentication
systems in order to accomodate a Franklin feature they're not using.

##### How helpful can we expect to be when something goes wrong?

Resolving user information via http and the request flow with the libraries we use would allow us to be very helpful. Debug logging when we receive a response,
when we try to decode the response, and of non-sensitive values in the body
would let us ensure that users have access to everything they need in order to tell
whether surprising authentication problems are in Franklin or in their service. We can also set
up the request expectations such that users can find out what field was the problem. For instance,
in the linked PR above, changing the response from `userId` (correct) to `userID` results in the
service returning a pretty clear message:

```bash
$ http :8080/api/users Authorization:"Bearer good token"
HTTP/1.1 400 Bad Request
Content-Length: 63
Content-Type: text/plain; charset=UTF-8
Date: Mon, 06 Jul 2020 19:42:59 GMT

The authentication server didn't respond as expected at .userId
```

#### We implement a few strategies and hope we did enough

In this option, we provide a few different authentication implementations
out-of-the-box, and users choose among them with command line flags.

##### How much harder is it to set up Franklin?

In the no-authentication case, it is identically easy to set up Franklin. If a user wants authentication to occur, and their authentication story is covered
by what we've implemented, then it's not much harder to set up Franklin.
If their authentication isn't covered, then their choices are to open issues
and hope we get to them soon, pull down Franklin and edit it locally, or use something else.

##### How much harder is it to deploy Franklin?

If a user's case is covered, then deploying Franklin in this story requires running
the server command with an extra command line flag. It is therefore not harder to deploy Franklin in this story.

##### What restrictions are placed on the user as a result of this choice?

Users' authentication strategies must fit in one of several very constrained boxes.
The space of authentication strategies is hardly infinite, and a few strategies (third party JWT, local JWT, opaque token) might cover a huge number of cases, but small variations would be difficult to accomodate, which
would shunt some users into requiring custom software to get use out of Franklin.

##### What restrictions are placed on us as a result of this choice?

No additional restrictions are placed on us as a result of this choice. We get to treat
the authentication strategy as 100% known and well-modeled by the server logic we
already have in place. For us it will not end up being in the way at all, though
we will need to maintain a few different authentication strategies for users who
know their requirements better than we do.

Decision
-----

For configuring domain levels, we should accept arguments indicating "domains" that
should be behind authentication. For configuring how to authenticate, we should
ask users to point Franklin to a service that can accept the authentication request
we'd like to send.

I chose this option primarily for two reasons. First, Franklin
will almost always and everywhere be an auxiliary service. People will use Franklin
as a STAC API that augments what's possible in some other API or frontend application. That other application likely already exists and has its own
solution to authentication. Franklin shouldn't force people to reimplement
solutions to problems they've already solved elsewhere.

Second, not everyone writes for the JVM. The relative volume of non-Azavea-authored issues
and forks on `PySTAC` (_some_ issues, _many_ forks) vs `stac4s` (zero of either) and Franklin (four issues from two people, one inactive fork)
tells us something about engagement we can expect if something requires writing
Scala. [`staccato`](https://github.com/planetlabs/staccato/network/members) similarly
has seen minimal community engagement, more or less entirely maintained by `@joshfix`,
with very few issues and forks from a short list of people. In short, if "classloader" ends up within
20 miles of the "starting Franklin with authentication" docs, I think we'll
be dramatically limiting Franklin's potential audience.

Asking people to provide a route to an authentication service keeps the docs 100%
in HTTP-land. Additionally, we can provide a very dumb authentication service
in a container that ensures that the startup instructions continue only to require
running Docker commands.

Consequences
-----

The first consequence of this is that Franklin will in an instant become distributed. While
in practice deployment will probably mean sidecar containers for people like us
who never learned to `k8s`, these sidecars are still an additional service dependency.
As a result, we should **require** the authentication service to expose
a healthcheck and include it in Franklin's healthcheck, if such a service has
been configured.

Second, introducing additional services increases the cost of not having tracing configured.
At the time that we created Franklin, we had some sort of conflicts with our
tracing library, tapir, and cats versions that caused us to be unable to include
tracing. That was disappointing, but acceptable for our small API that didn't talk to anything but a database.
With an additional
service which might have its own performance and reliability statistics, we
should ensure that Franklin is capable of hooking into OpenTelemetry-compliant
tracing systems. There is a [new entry](https://github.com/janstenpickle/trace4cats) in this domain that we should consider as well, now that we have
some experience in this domain.

Third, we'll need to develop an example authentication service and add it to the getting started documentation.
We'll need to advertise and document the semantics of the authentication service somehow.
The most straightforward way to do this would be with an example service that:

1. is sufficient for Franklin's needs
2. advertises an OpenAPI specification

An added bonus of this is that we'll have _two_ services, one of which calls the other, that we can use to learn more about the capabilities that OpenTelemetry enables.

Finally, we'll need to adapt the work in the [toy repository](https://github.com/jisantuc/configable-auth) for Franklin. Choosing a configurable HTTP authentication service
will introduce some latency to requests for servers that have it configured. As a result, the adaptation should involve some kind of authentication result caching that
respects any TTL returned by the authentication service.
