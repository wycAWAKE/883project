# M3 Room Booking Test Scenarios

Apply `smartcampus_m3.sql` to a disposable/local `dormitory` database and run Spring Boot with the matching datasource settings. Sign in through the app so requests carry a server Session cookie. Use two student accounts for ownership and conflict cases.

| Case | Steps | Expected result |
| --- | --- | --- |
| Availability | Select an active room and a date from today through 30 days | Four slots are shown initially; all fourteen consecutive one-hour slots from 08:00 to 22:00 are available through the expand control, and booked/past slots are disabled |
| First booking | Book one available room/date/slot | One `BOOKED` row is added and appears under My bookings |
| Same-session duplicate | Submit the same room/date/slot again | Request fails; the existing booking remains the only active row |
| Concurrent conflict | From two independent browser sessions submit the same room/date/slot at nearly the same time | Exactly one succeeds; MySQL's unique active-slot key rejects the other |
| Cancel and rebook | Cancel an owned booking, then book the same room/date/slot again | Old row remains `CANCELLED`; new row is `BOOKED` |
| Ownership | Sign in as student B and cancel student A's booking ID | Request fails and A's booking stays `BOOKED` |
| Repeated cancel | Cancel an already cancelled booking | Request fails without changing history |
| Past / beyond range | Request yesterday or a date more than 30 days ahead | Request fails before database write |
| Invalid slot | Submit a time not in the four fixed slots | Request fails before database write |
| Unauthenticated | Call `/bookings/mine`, POST `/bookings`, or cancel without Session | Request fails and no booking changes |

Automated tests cover date-window, fixed-slot, room validation, ownership, error translation, and the concurrent race against the real MySQL generated-column unique constraint. `BookingServiceMySqlIntegrationTest` also verifies a cancelled row remains in history and the released slot can be booked again. Run it only after applying `smartcampus_m3.sql` to the configured test database. The unauthenticated and cross-account ownership API cases should also be checked with separate browser sessions using the steps above.
