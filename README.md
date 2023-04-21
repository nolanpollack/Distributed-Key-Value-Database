# Distributed-Key-Value-Database

## Description

This is a distributed key-value database. It is an approximate implementation of the Raft consensur algorithm. 

## Usage

```./3700kvstore <UDP port> <your ID> <ID of second replica> [<ID of third replica> ...]]```

## Approach

The program starts by initializing a Replica object. It then runs in one of three states, starting as a follower. 
This is done non-concurrently, as the program is single-threaded. The program then listens for UDP messages on the port specified by the user.
The program then checks the message type, and calls the appropriate function, possibly changing itss state. The program then returns to listening for messages.

## Challenges

I had many small bugs that made completing the project difficult. Some of the more notable ones were:

- Followers were committing the same entry multiple times. It turns out that when resending a group of entries, a follower 
would erase its log to a point, and then set its commitIndex to the size of the log.
- After starting the program, it takes 5 seconds for an election to start. It turns out that this is because the follower checks for an election timeout,
then waits for a message in a loop. This waiting for the message is blocking, so the election timeout isn't checked until a follower
receives a put from the client. I ended up not changing this, because multiple possible solutions introduced new problems,
and this actually doesn't affect any performance or correctness, it just means it takes 5 seconds to start. 
- I had several bugs when dealing with how leaders deal with entries after a leader change. These were all small things
that ended up getting fixed. 

## Testing

I ran the test suite probably hundreds of times, ensuring that everything was done correctly. I also created detailed logs,
to be able to tell exactly what when on through all points when testing. From there, I figured out what caused the problems
and worked my way down, ensuring everything was according to the raft paper.