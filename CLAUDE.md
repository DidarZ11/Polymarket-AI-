# Polymarket AI Agent — Project Context

## Project
Spring Boot AI agent that analyzes Polymarket prediction markets.
Stack: Java 17, Spring Boot 3, PostgreSQL, Maven, Claude API (tool use), Telegram Bot, React dashboard.

## Architecture
- PolymarketClient → fetches markets from clob.polymarket.com
- AgentService → orchestrates Claude API tool use
- MarketRepository → persists to PostgreSQL (polymarket_db, localhost:5432)
- TelegramBot → sends alerts
- React frontend → dashboard

## Code Style
- Language: Java, comments in Russian
- Use Lombok (@Data, @RequiredArgsConstructor)
- Constructor injection, no @Autowired on fields
- Return ResponseEntity from controllers
- Wrap external calls in try/catch, log errors with Slf4j

## Database
- DB: polymarket_db, user: postgres, port: 5432
- Migrations via SQL scripts in resources/db/

## Do NOT
- Do not add unnecessary dependencies
- Do not write tests unless asked
- Do not explain basics — just write working code