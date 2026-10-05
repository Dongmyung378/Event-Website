[English](PORTFOLIO.md) | [한국어](PORTFOLIO.ko.md)

# EventLite — Project Process and Individual Contribution

EventLite is an event and venue management team project for COMP23412 Software Engineering at the University of Manchester. This document distinguishes the complete team implementation, Dongmyung Park's individual contribution, and subsequent maintenance. See the [README](../README.md) for run commands and the [architecture document](ARCHITECTURE.md) for code locations and environment settings.

[GitHub repository](https://github.com/Dongmyung378/Event-Website) · [Project on my portfolio](https://park-dongmyung.com/projects/project-10/)

## Timeline and Work Scope

| Stage | Period or verification date | Work |
| --- | --- | --- |
| Original team project | February–May 2025 | Implemented event and venue management on the supplied EventLite starter code |
| Subsequent maintenance | Verified on 2026-10-05 | Fixed issues found during code review, separated external service settings, and improved regression tests and run documentation |

The current repository includes work from both stages. The test counts and coverage figures describe the maintenance version verified on 2026-10-05 and are not presented as results from the original 2025 assignment.

## Individual Role

**Dongmyung Park contributed test cases, security implementation, and core functionality other than map integration.** His work included implementing and verifying features such as event and venue management. Map integration was handled by another team member.

| Contribution area | Description |
| --- | --- |
| Test cases | Tests for event and venue queries and changes, required inputs, missing targets, data relationships, and other functional conditions |
| Security | Security-related implementation, including login and access control for management features |
| Core functionality | Implementation and verification of the service's core features, including event and venue management |
| Team map feature | Another team member's responsibility; presented as a feature of the complete service |

The complete service is the team's work. The architecture and maintenance sections below describe the current service and do not attribute every source file or all subsequently added tests to an individual's original contribution.

## Problem to Solve

Events need dates, times, descriptions, and venues to be managed together. Visitors need to search by event name and browse venue schedules, while administrators need to create, update, and delete records. Data relationships also require rules such as preventing the deletion of a venue that still has events.

The same data needs to be available through web pages and an API, while changes must be restricted to administrators. This requires both displaying controls according to login state and checking authorization on the server. Maps and social services extend the core functionality, so account configuration or failed responses should not break basic browsing and management flows.

## Implementation Approach

1. Defined the relationship between `Event` and `Venue` and used JPA repositories to query and persist data.
2. Connected lists, search, detail pages, and CRUD flows through MVC controllers and Thymeleaf forms.
3. Applied authentication, roles, and CSRF checks to web requests that change data.
4. Used a shared service layer for HTML pages and the read-only HAL JSON API.
5. Separated service unit tests, controller tests, and real HTTP integration tests to verify functionality and request flows.

Basic CRUD alone did not cover every condition a user could encounter. Maintenance work reproduced and fixed conditions involving small datasets, changing dates, and failed external requests.

## Conditions Improved During Maintenance

The following changes are included in the current version verified on 2026-10-05.

| Condition | Issue found | Current handling |
| --- | --- | --- |
| Deletion and social posting requests | Management controls and actual HTTP routes had different protection coverage | Administrator role checks on the actual methods and routes; undefined requests denied |
| Account settings | Tokens and administrator accounts were hardcoded | Environment variable configuration and separate, explicit demo and test accounts |
| Map popups | Stored names were inserted as HTML strings | DOM and `textContent` display names as text |
| October–December and events on the current day | Date calculations and upcoming-event rules were inconsistent | Shared date handling and month and year boundary tests |
| Zero to two venues | Popular-venue lookup always indexed three entries | Returns up to three entries according to the actual dataset size |
| Multiple events on the same date | Next-event lookup did not adequately account for time order | Sorts by date, time, and name, then returns up to three entries |
| IDs in update requests | Form IDs could conflict with URL IDs | Loads the existing target using the URL ID and updates only allowed fields |
| Address changes and failed lookups | Previous coordinates remained attached to a new address | Removes previous coordinates when coordinates for the changed address cannot be obtained |
| Failed social posts | Failures appeared successful, or posting occurred before checking that the event existed | Checks existence and input before posting, then displays failure feedback when needed |
| Initial data and dates | Only some venues existed, or dates in successful tests had become past dates | Demo initialization without null venue references and data based on relative dates |

The initial map integration was a team member's contribution. Later fixes and verification of map boundary conditions belong to subsequent maintenance. These are kept separate when describing individual contribution and project timing.

## Maintenance Verification

The verification date is **2026-10-05**. The [verification JSON](verification.json) records the execution environment and results.

| Item | Record |
| --- | --- |
| Environment | JDK 17.0.13, Maven 3.9.9 |
| Java | 212 passed; 0 failures, errors, or skipped tests |
| JavaScript | 10 passed; 0 failures |
| Overall Java line coverage | 95.24% |
| Overall Java branch coverage | 72.68% |
| Browser | Login and event creation → update with the same ID → deletion |
| Layout widths | Desktop at 1440px and mobile at 390px; no horizontal overflow |
| Browser JavaScript errors | 0 |

Java verification includes unit, controller, integration, and security tests. JavaScript verification checks map date boundaries, empty results, zero and invalid coordinates, popup text, and map initialization failures. Tests use separate in-memory databases and simulated HTTP responses, without requests to real external service accounts.

Coverage measures the proportion of code executed; it does not establish complete functional correctness or operational stability. In particular, these records do not guarantee responses from real Mapbox or Mastodon accounts, production traffic behavior, production database and backup operations, or behavior on a wide range of physical mobile devices. GitHub CI results are separate from this local execution record.

## Retrospective

Authorization should be verified against actual request methods and routes, as well as what the interface displays. Hiding a button from a user and having the server reject a change request are different conditions.

Tests that pass with normal data can miss small datasets, changing dates, and external request failures. Reproducing failure conditions and retaining them as regression tests made it possible to verify the effect of fixes.

Presenting a team project also requires distinguishing the complete functionality, individual roles, and subsequent maintenance. This repository maintains those distinctions while providing run instructions and verification records as portfolio material.

## Next Operational Steps

A public deployment needs TLS, a production database and backups, administrator account management, request limits, and dependency updates appropriate to its environment. Real map and social account integrations need additional verification in an environment using the relevant account settings.

Attribution for the supplied course code and its BSD license are retained in the [README attribution section](../README.md#attribution-and-license) and the [original license](../LICENSE).
