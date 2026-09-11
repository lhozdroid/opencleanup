.PHONY: codex

codex:
	@codex --dangerously-bypass-approvals-and-sandbox \
		--model gpt-5.6-luna \
		-c 'model_reasoning_effort="xhigh"'
