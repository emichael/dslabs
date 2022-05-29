---
layout: default
---

DSLabs is a framework for creating, testing, model checking, visualizing, and
debugging distributed systems lab assignments. The labs have been used to teach
hundreds of students in the [University of Washington's CSE
452](https://courses.cs.washington.edu/courses/cse452/) and courses in
universities around the world.

It is designed from the ground up help students create *correct and runnable*
implementations of real distributed systems. DSLabs incorporates automated
**model checking** to search students' implementations for bugs and report back
with example traces when bugs are found.

The labs are written in Java and come packaged all necessary dependencies. They
also include a **visual debugger**, which allows students to explore the
reachable states of their systems and visualize bugs reported by the model
checker.

## Labs

There are currently 4 labs in the DSLabs package. These labs have students
implement:

1. A simple key-value store and at-most-once RPC service
2. A Primary/Backup system
3. Paxos-based State Machine Replication
4. A Sharded reconfigurable key-value service with distributed transactions

Parts of this sequence of assignments (especially labs 2 and 4) are adapted from
the [MIT 6.824 Labs](http://nil.csail.mit.edu/6.824/2015/).

Labs 2, 3, and 4 depend on parts of lab 1, and lab 4 depends on lab 3. The labs
increase in difficulty; lab 2 primarily functions as a training exercise before
implementing Paxos. Part 3 of lab 4 (distributed transactions) is particularly
challenging. In some years, it has been extra-credit at UW.

See [the GitHub repository](https://github.com/emichael/dslabs) for more
information.

## Advice and Materials for Teachers

If you are looking to incorporate DSLabs into a course you teach, the first
thing to do is to complete the labs yourself. The [main
`README`](https://github.com/emichael/dslabs/blob/master/README.md) and the
[students'
`README`](https://github.com/emichael/dslabs/blob/master/handout-files/README.md)
contain more information about getting started.


### Suggested Reading

The lab `README`s help provide some background, but to ensure students have the
necessary information to complete the labs, we strongly suggest assigning "Paxos
Made Moderately Complex" and chapter 7 of Bernstein, Hadzilacos, and Goodman's
"Concurrency Control and Recovery in Database Systems", up to but not including
the section on 3-phase commit. Labs 1 and 2 are fairly self-contained, however.
See recent offerings of [UW CSE
452](https://courses.cs.washington.edu/courses/cse452/) for full example
syllabi.


### Distribution

The default GitHub branch is not setup for use by students. Instead, we create a
handout for distribution to students that comes packaged with all dependencies.
This handout is automatically deployed to the [`handout` branch of the
repository](https://github.com/emichael/dslabs/tree/handout) but is periodically
overwritten.

Our suggested distribution strategy is to create a private clone of the main
repository for use by course staff and a repository viewable by students. You
can make changes to your private repository and merge in changes from upstream
at will. Then, using a script like the following, you can push new versions of
the built handout to students as necessary. They will only see the fully built
version of DSLabs and will be able to merge in changes.

```bash
make build/handout/
HASH=`git rev-parse --short=8 HEAD`
DATE=`date "+%Y-%m-%d %H:%M:%S"`
git checkout -b temp
git add -f build/handout/
git commit -m "Handout temp"
git clean -fxd
git filter-branch -f --subdirectory-filter build/handout/
git remote add handout $(HANDOUT_REPO)
git fetch handout master
git checkout -b handout handout/master || git checkout --orphan handout
git rm -rf .
git checkout temp -- .
git commit --allow-empty -m "Publishing handout on $(DATE) from $(HASH)"
git push handout handout:master
````

**Please ensure your students do not publicly post solutions to the labs.** The
continued success of this project is dependent on keeping solution sets off of
the internet.

### Grading

Several grading scripts are included in the DSLabs repository, and the
`Makefile` distributed to students includes a target that packages up a
`submit.tar.gz` file for submission. The grading scripts may need to be adapted
to your particular infrastructure. Please note that the grading scripts are not
perfect. Bounded model checking is not guaranteed to find all bugs. Also, it is
entirely possible to circumvent the spirit of the DSLabs framework (e.g., by
using `static` for intra-Node communication) or **circumvent the tests
entirely** (e.g., by using reflection to overwrite the testing infrastructure
and always report success). You should always check to ensure submitted code is
not pathological or malicious.


### Discussion Slides

Over the years, our intrepid UW TAs have led discussion sections as
introductions to the lab assignments. The slides they created have a number of
useful tips and implementation strategies, based on their own experience. While
the lab `README`s are complete and address most of the major pitfalls, we
provide [the discussion slides from a recent iteration of the
course][discussion-slides] as an example of the kinds of things you might want
to discuss with students about the labs.



[discussion-slides]: {{ '452 Discussion Section Slides.pdf' | relative_url }}
