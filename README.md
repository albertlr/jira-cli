# Jira Command Line Interface (CLI)

## Why
I'm usually struggling to automate the repetitive jobs when involved Jira.

Lately I had to clone and move hundreds to tickets from one project to another while keeping it's references. Manually it was a pain .. doing it with bash through REST API .. again it was pretty hard to manage.

After some search I found that Atlassian actually provide an API for that in Java .. so I started to use it.

## Purpose

The purpose of this project is to be a skeleton project for more complex tasks when you have to clone, move and link tickets all together.

## Actions covered so far

### get 

It's basic purpose is to get the contents of a ticket in Jira. THe output currently is limited to the ticket and it's links .. however it is primarily used by other actions to load a ticket from Jira.
```
./jira-get.sh <TICKET_ID>
```

### get all E2Es

This is particular to the project I'm working on. We have linked End to End testcases to bugs (known as internal defects or customer defects). As some of the test cases have dependencies on one another, this utility is ment to get recusively all the E2Es associated to a defect, and all it's dependencies.

```
./jira-get-e2es.sh <TICKET_ID>
```

### clone

Clone a ticket, preserving links by default. At source level is customizable though. 
TODO manage it through properties. 

```
./jira-clone.sh <SOURCE_TICKET_ID>
```

### move

Clones a ticket and moves it to another project (it creates the ticket directly in the new project).

```
./jira-move.sh <SOURCE_TICKET_ID>
```
(currently destination project is hardcoded in the bash script; sorry)
