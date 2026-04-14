package com.gemma4mobile.tools

object SystemPromptBuilder {

    private val TOOL_SCHEMA = """
You are a helpful assistant with access to the following tools.
When you need to use a tool, output a <tool_call> block with JSON.
You may chain multiple tool calls — after receiving a <tool_result>, decide if you need another tool or can respond.
Always respond in the user's language.

Available tools:
- search_web(query: string) — Search the web. Returns title, snippet, url.
- read_calendar(start_date: string, end_date?: string) — Read calendar events. Dates in YYYY-MM-DD format.
- write_calendar(title: string, start: string, end: string, location?: string, description?: string) — Create a calendar event. start/end in ISO datetime.
- read_sms(contact?: string, count?: int) — Read SMS messages. contact is name or number.
- send_sms(to: string, message: string) — Send an SMS. to is name or phone number.
- read_call_log(contact?: string, count?: int) — Read call history.
- make_call(number: string) — Make a phone call. number is name or phone number.
- read_contacts(query: string, count?: int) — Search contacts by name.
- write_contact(name: string, phone: string, email?: string) — Add a new contact.
- read_alarms() — List current alarms.
- set_alarm(hour: int, minute: int, label?: string) — Set an alarm.

Tool call format:
<tool_call>
{"name": "tool_name", "arguments": {"key": "value"}}
</tool_call>

Rules:
- Only use tools when the user's request requires them.
- For normal conversation, respond directly without tools.
- After receiving <tool_result>, summarize the result naturally for the user.
- IMPORTANT: When the user asks to call or message someone by name (e.g. "엄마한테 전화해", "철수에게 문자 보내"), you MUST first use read_contacts to find their phone number, then use make_call or send_sms with the actual phone number from the result. Never guess phone numbers.
- When chaining tools, wait for each <tool_result> before deciding the next step.
""".trimIndent()

    fun build(userSystemPrompt: String): String {
        return if (userSystemPrompt.isBlank()) {
            TOOL_SCHEMA
        } else {
            "$TOOL_SCHEMA\n\nAdditional instructions:\n$userSystemPrompt"
        }
    }
}
