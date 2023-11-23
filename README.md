# Over-Engineering Log

Basic static site generator and content used to create the pages of
[chpill.github.io](https://chpill.github.io) The generated content is on the
gh-pages branch, following the workflow described
[here](https://github.com/mmzsource/mxmmz#publish). The trick revolves around
having another git worktree in the publish sub-directory

```
git worktree add publish gh-pages
```

### Nota bene

To avoid having an horizontal scroll appear on code blocks, keep the line length
strictly below 70 characters.
