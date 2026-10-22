# The memory reading

Each sprint's roadmap note records what the default stack uses, since it has to fit in the 8 GB
Docker VM. A ticket that adds a container takes one before and one after; the definition-of-done
pass takes the sprint's. Readings compare only when taken the same way.

## Steps

1. **Idle.** Start the stack with `make up`, wait 15 minutes with nothing running against it, then
   run `make memory`. This is the reading to compare across a change.
2. **Under load.** Run both Playwright suites one after the other, each with `--workers=1`
   (`npx playwright test --workers=1` in `frontend/storefront`, then `frontend/admin-console`), then
   run `make memory` at once.
3. **Record** both in the roadmap note:
   - the totals;
   - each new container's use against its cap;
   - any container over 90% of its cap (the report marks them);
   - what the caps add up to;
   - the swap in use.

   For the "before" reading of a ticket, take step 1 at the commit before it, with the stack
   built from that commit.

A cap is raised when its container nears it idle or is killed under the suites. `make status` names
a container killed for memory. Say in the note when the caps add up to more than the VM has.
