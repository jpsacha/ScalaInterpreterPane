Things to improve:

- when completing `Li` to `List` and pressing <kbd>Tab</kbd> again,
  we end up with `Listval List`. What we want is to determine first that
  the symbol is already complete, and then see if it denotes an object
  that has an apply method, then filling in the parameters of `apply`.
- the `apply` thing is particularly important for libraries such as
  Patterns, where we want to go from `Brown` to `Brown(lo, hi, step)`
  (and possibly not show the implicit argument list).
- completing `List.tabulate`, you end up with a half-broken parameter
  list such as `List.tabulate(n1,n2,n3,n4,n5)(f, Int, Int, Int, Int) => A)`,
  so something goes from here for the `f` parameter of the second list.
