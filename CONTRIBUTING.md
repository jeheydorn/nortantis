# Contributing to This Project

Thank you for your interest in contributing! Before you start writing code, please read the sections below — especially the one about discussing your design first.

To ensure that all contributions are legally sound and can be used in both open-source and closed-source versions of this project (if one is ever made), we also require all contributors to sign our Contributor License Agreement (CLA).

## Discuss Your Design Before Submitting Code

If you'd like to contribute anything beyond a small fix (such as a typo correction, minor bug fix, or small cleanup), please reach out to me (the project owner) first — by opening an issue or starting a discussion by email or Reddit — and describe what you'd like to do and how you plan to do it. I'm happy to talk through ideas, but I'd like a chance to confirm that the change fits the direction of the project before you invest significant time in it.

This is mainly to protect your time. Nortantis has a particular scope and aesthetic, and not every feature or change is a good fit, even if it's well written. It's much easier (and less awkward for both of us) to agree on a general design for a change up front than to ask for major revisions — or to decline the contribution entirely — after a large pull request has already been written.

Small, self-contained fixes are welcome without prior discussion. When in doubt, ask first.

## Contributor License Agreement (CLA)

- All contributions to this repository must be covered by our CLA.
- The CLA ensures that:
  - You retain authorship credit for your work.
  - You grant the project owner an **exclusive copyright license** to your contributions.
  - The project owner may license your contributions under any license, open source or closed source. For example, this allows Nortantis to be licensed for use in closed-source apps, released in a future closed-source edition, or moved to a different open source license.
  - You receive a license back to use your own contribution however you like.

### Signing the CLA

- When you open a Pull Request, [CLA Assistant](https://cla-assistant.io/) will automatically check whether you have signed the CLA.
- If you haven’t signed yet, you’ll see a comment with a link to the agreement.
- Please read the agreement carefully and sign it before your PR can be merged.

### Notes

- Even small changes (like typo fixes) require signing the CLA, since every contribution is legally significant.
- If you are contributing on behalf of an organization, please contact me before opening a pull request so the CLA can be accepted by someone authorized to act for that organization.
- The CLA was replaced in October 2026. If you signed the earlier version (the FSFE Fiduciary License Agreement 2.0), CLA Assistant will ask you to sign the new version on your next pull request. Contributions made under the earlier version remain covered by that version.

## Contribution Workflow

1. Fork the repository and create your branch.
2. Make your changes and commit with clear messages.
3. Open a Pull Request.
4. Sign the CLA when prompted.
5. Once the CLA is signed and your PR is reviewed, it can be merged.

## Use of AI Coding Tools

AI coding assistants (such as GitHub Copilot, Claude, ChatGPT, and Cursor) are allowed when preparing contributions. However, you are fully responsible for every line of code you submit, regardless of who or what wrote it. By opening a pull request, you are vouching for the code as if you had written it by hand.

In particular, this means you must:

- **Understand the code.** You should be able to explain what every part of your patch does and why.
- **Review it carefully.** AI-generated code is often plausible-looking but subtly wrong — it may ignore APIs it should have used, create convoluted magic numbers, ignore project conventions, or quietly break behavior elsewhere.
- **Test it.** Run the relevant tests, exercise the feature manually, and confirm that the change actually does what you intend.
- **Make it maintainable.** If something breaks later, the AI won't be there to fix it — you will, or I will.

This is in line with how many open-source projects, including the Linux kernel community, treat AI-assisted contributions: AI is a tool used by the contributor, not the contributor itself. Submissions that appear to be low-quality, unreviewed AI output may be asked for substantial revision or closed without merging.

---

By contributing, you confirm that you have read and signed the CLA and that your submissions are legally covered.
