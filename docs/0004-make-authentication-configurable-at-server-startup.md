# 4. Make Authentication Configurable at Server Startup

Date: 2020-06-29

## Status

Accepted

## Context

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
ADT called `Domain` and choose up to several of them from the command line at server startup that I don't believe this question deserves much further
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
to run can access it. I think this is a four line Dockerfile. You also need to be
able to host that container image somewhere possibly private. I believe we can take that skill for granted.

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
whether surprising authentication problems are in Franklin or in their service.

## Decision

The change that we're proposing or have agreed to implement.

## Consequences

What becomes easier or more difficult to do and any risks introduced by the change that will need to be mitigated.
