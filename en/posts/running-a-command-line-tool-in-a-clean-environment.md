---
author: Etienne Spillemaeker
title: Running a command-line tool in a clean environment
published: 2025-01-29
---

**TLDR**: Use `env -i <command and its arguments>` to run a command without the
environment variables from your current shell.


I was manually doing some maintenance on some remote Linux servers at work, and
one of the thing I had to do was to disable password authentication, to make
sure they were only accessible if you had your SSH public key in there. This is
very easy to do:

- set `PasswordAuthentication no` in /etc/ssh/sshd_config
- Enable the new configuration `systemctl restart ssh`

But I also wanted to test that it was producing the intented effect, by trying
to SSH in via password, see it succeed, and then fail after the new settings are
enabled. For the first server, I actually kept switching on and off identities
in my ssh-agent, which was quite tedious. For this specific case, SSH has an
option to make it ignore the agent (`-o IdentityAgent=none`), but there is in
fact a more generally applicable solution to this problem, using `env` from [GNU
coreutils][1]: `env -i ssh <remote-user-and-host>`.

I had only ever used `env` to see my current environment variables, and, funnily
enough, a great way to demonstrate what `env -i` does is to run `env -i env`,
which will output nothing. Hurray!

[1]: https://www.gnu.org/software/coreutils/manual/html_node/env-invocation.html#env_003a-Run-a-command-in-a-modified-environment
