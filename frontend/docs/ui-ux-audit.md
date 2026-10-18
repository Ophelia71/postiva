# Postiva UI/UX audit — white theme

Date: 2026-09-21

Scope: React + TypeScript + Tailwind frontend. Business logic, API contracts, routes, and existing product features must remain unchanged.

## Product context

Postiva helps sellers, small shop owners, and beginner marketers create content, schedule posts, and publish to Facebook, Instagram, and Threads. The UI should feel modern SaaS, professional, friendly, easy to read, and intentionally light/white.

## Design system direction

- Theme: white/light only. Do not auto-switch to dark mode from OS preference.
- Primary: indigo `#6672D8` for main CTA, active navigation, focus accents.
- Primary hover: `#505DB8`.
- Secondary: cyan `#0891B2` for supportive highlights.
- Success: emerald `#059669`.
- Warning: amber `#D97706`.
- Error: rose `#E11D48`.
- Neutral: slate/blue-gray for text, borders, muted copy, and surfaces.
- Background: app canvas `#F6F8FC`, panel/surface `#FFFFFF`, soft surface `#EEF2FF`.
- Typography: Manrope/system sans, 16px body baseline, 1.5+ line-height, bold compact headings.
- Spacing: 4/8px rhythm; larger groups use 16/24/32px.
- Radius: 12px controls, 16–20px cards, 24px modals.
- Shadow: soft low-contrast shadows, no heavy glow in the app shell.
- Icons: keep Lucide as the single icon family; decorative icons should be `aria-hidden`.
- Motion: subtle transform/opacity only and respect `prefers-reduced-motion`.

## Critical

- Modal focus management and Escape behavior should be standardized across edit/detail/delete/repost flows.
- Icon-only buttons need explicit accessible names, not only `title`.
- Destructive and failed actions should avoid native `alert`/`confirm`; use modal/status feedback.
- Manual social connection fields must keep visible labels, not placeholder-only labels.
- Mobile touch targets should be at least 44px for main and destructive controls.

## High

- Table-like post rows use fixed desktop grids and need a mobile card/stack layout to avoid horizontal overflow at 375px.
- Create-post flow is dense; AI/upload/publish progress should be visible and double-submit must be prevented.
- Loading states are mostly text-only; shared skeleton/loading patterns should be added for longer waits.
- Color/radius/shadow values are duplicated across pages and should move toward semantic tokens.
- Navigation should keep create-post easy to reach on mobile with one hand.

## Medium

- Status and platform badges rely heavily on color; keep text labels/icons so state is not color-only.
- Publish result feedback should list each platform result clearly.
- Forms need helper text near complex fields and clearer recovery guidance in errors.
- Chart/report screens should include accessible labels and meaningful empty/error states.

## Low

- Marketing page has more decorative motion than the app; keep app screens calmer.
- Some landing/footer visual glyphs should eventually be replaced by vector icons.
- Empty states can add direct next-step actions such as “Create post” or “Connect channel”.

## Implementation phases

1. White-theme foundation: semantic CSS tokens, focus states, spacing/radius/shadow standards.
2. Shared UI primitives: button, icon button, field, modal, status message, pagination, skeleton.
3. Auth and connection UX: labels, helper text, async feedback, no native confirm.
4. Create-post UX: progress for AI/upload/publish, per-channel publish result display, double-submit prevention.
5. Data views: mobile card layout for tables, accessible chart controls and empty/error states.
6. QA: TypeScript check, lint, production build, then responsive visual review at 375/768/1024/1440.
