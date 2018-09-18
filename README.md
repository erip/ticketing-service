# Ticket Service

## Description

Implement a simple ticket service that facilitates the discovery, temporary hold, and final reservation of seats within a high-demand performance venue.

## Design

### High level

I intend to implement this as a reactive system due to the high-demand nature of the venue. By designing it in a reactive way, we can ensure resliency, elasticity, message-driven (i.e., non-blocking) communications, and most importantly responsiveness.

To do this, I will realize the service as a RESTful [Lagom](https://www.lagomframework.com/) microservice backed by Kafka as the message broker and Cassandra as the event store.

### Mid level

I will expose RESTful API endpoints for the required interface's public methods; other endpoints will be added on an ad hoc basis as determined necessary.

These endpoints will be documented in a [Swagger](https://swagger.io/) specification against which consumers may design their services and consume the ticket service's API.

### Low level

Because a part of the design includes a subjective determination of "best N seats", my initial design will simply optimize for the customer (and his guests) to be able to see the movie: if seats are available, they will be taken greedily with no preference for proximity to the customer. This optimizes for seeing the film over the experience of viewing with friends and is a simple "approximation".

My better implementation is still greedy, but prefers seats that are adjacent or directly behind others in your party. It does this through a clever seat numbering and ordering scheme in which every other row's seat numbers are presented in a reverse order; e.g.,

```
 SCREEN
========
1  2  3  4
8  7  6  5
9 10 11 12
```

This allows the seats to be ordered as a 1D array and simply popped from the front in-order. This design is still suboptimal in the following case: requesting two seats from

```
 SCREEN
========
x  x  x  4
8  7  6  5
9 10 11 12
```

would yield seats `4` and `5`, which aren't neighboring; i.e., it's difficult to penalize a cross-row recommendation.

Some graph algorithms could be explored to improve this solution; in particular, this specific problem seems particularly similar to a Hamiltonian path problem. Experience shows that when going to a theater, we would prefer to sit side-by-side (i.e., row-wise) with friends rather than front-to-back (i.e., column-wise); this is an extra constraint in the path problem.

An important note is that finding a Hamiltonian path is NP-complete; because the number of seats in a theater is typically fairly small, this may or may not be solved through brute force and distributed compute. If it is not possible, an approximation will need to be used.

## Assumptions

- Because everyone likes to whisper to their pals at the movies, "best" tickets are those which are neighboring (or directly behind) others in your party. 

- A customer may have a single hold (don't be greedy!), but may have any number of reservations. 

## Requirements

To run this project, [`docker`](https://docs.docker.com/install/) will need to be installed.

Alternatively, `sbt` can be used locally.

## Building the docker image

To build the docker image, execute `bin/build_docker_image.sh`.

To build it manually, issue

```sh 
docker build -t 'erip-homework' .
```

To confirm it successfully build, check the output of `docker images`:

```sh 
$ docker images | grep erip
erip-homework          latest               31f8de52e758        5 seconds ago       765MB
```

## Running tests

If the docker image has been built, tests can be run by executing `bin/run_tests_docker.sh` or by 
manually issuing

```sh
docker run -it 'erip-homework' test
```

If sbt is being used, execute `bin/run_tests_sbt.sh` or manually issue `sbt test` from the project root.

To enable coverage locally with `sbt`, issue `sbt clean coverage test coverageReport coverageAggregate` and open `target/scala-2.12/scoverage-report/index.html`.

## Authors

- [Elijah Rippeth](elijah.rippeth@gmail.com)