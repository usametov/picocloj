# Using Skills in PicoClaw

Skills are modular prompts that enhance PicoClaw's capabilities for specific tasks. They follow the open **Agent Skills** format used across the Claude/OpenClaw ecosystem, making them portable and reusable.

## What Are Skills?

A skill is a **SKILL.md** file containing:

1. **YAML frontmatter** - Metadata (name, description, allowed tools, version)
2. **Markdown body** - Prompt injection that guides the LLM when the skill is active

Skills don't contain executable code - they're pure data that influences how PicoClaw behaves and which tools it can use.

## Getting Started

### 1. Initial Setup

When you first run PicoClaw, it creates a skills directory at `~/.picocloj/skills/` with two example skills:

```bash
clj -M -m astanova.picocloj onboard
```

This creates:
- `example/` - A demo skill that makes PicoClaw more enthusiastic
- `shell-helper/` - A skill that enables shell command execution

### 2. Starting the Agent

```bash
clj -M -m astanova.picocloj agent
```

The agent loads all skills from the configured directory and makes them available.

## Using Skills

### Command Interface

PicoClaw provides simple slash commands for skill management:

| Command | Description |
|---------|-------------|
| `/skills` | List all available skills |
| `/skill-name` | Activate a specific skill (e.g., `/example`) |
| `/clear` | Deactivate the current skill |
| `quit` or `exit` | Exit the agent |

### Example Session

```
🦐 PicoClaw Clojure edition ready! (type 'quit' to exit)
  Use /skills to list skills, /skill-name to activate, /clear to deactivate

👤 You: /skills

📚 Available skills:
  /example - An example skill that makes PicoClaw more helpful and enthusiastic
  /shell-helper - A skill that allows running shell commands for system tasks

👤 You: /shell-helper
🎯 Activated skill: shell-helper

👤 You: List files in the current directory
🦐 PicoClaw: I'll list the files using the `ls` command.
🔧 Executed tool: shell_command -> {:exit 0, :out "total 32\ndrwxr-xr-x ...", :err ""}
🦐 PicoClaw: Tool result: {:exit 0, :out "total 32\ndrwxr-xr-x ...", :err ""}

👤 You: /clear
🧹 Cleared active skill
```

### Programmatic Activation

Skills can also be activated via the `invoke_skill` tool:

```json
{
  "name": "shell-helper",
  "reason": "User wants to run system commands"
}
```

## Built-in Skills

### 1. Example Skill (`/example`)

A demonstration skill that shows how skills work. When active:
- PicoClaw becomes more enthusiastic and uses emojis
- Responses are more detailed and encouraging
- Great for understanding the skill activation concept

### 2. Shell Helper (`/shell-helper`)

Enables shell command execution for system tasks. When active:
- **Allowed tool**: `shell_command` only
- Provides safety guidelines for command execution
- Includes examples for common tasks (listing files, git status, creating directories)

**Safety Features**:
- Commands are executed via `clojure.java.shell/sh` (no shell interpretation)
- Arguments are passed as separate strings (prevents command injection)
- Includes warnings about destructive commands

**Example commands**:
- `ls -la` - List files with details
- `git status` - Check git repository status
- `mkdir test` - Create a new directory
- `pwd` - Show current working directory

## Creating Your Own Skills

### Skill File Structure

Create a new directory in `~/.picocloj/skills/` with a `SKILL.md` file:

```yaml
---
id: my-skill           # Unique identifier (lowercase, hyphens)
name: My Skill         # Display name
description: Does something useful
allowed-tools: [tool1, tool2]  # Optional: restrict which tools are available
custom-tools:          # Optional: extend existing tool schemas
  - name: simple_calc
    description: "Enhanced calculator with precision"
    schema:
      type: object
      properties:
        expression:
          type: string
          description: "Mathematical expression"
        precision:
          type: integer
          description: "Decimal places"
          minimum: 0
          maximum: 10
          default: 2
      required: [expression]
version: 1.0.0
author: Your Name
---
# Your skill's prompt injection

You are now in **my skill mode**. This skill enhances PicoClaw for specific tasks.

## Guidelines
- First guideline
- Second guideline

## Examples

**User**: "Example request"
**You**: "Example response with tool usage"

Remember: Your custom instructions here.
```

### Step-by-Step Guide

1. **Create skill directory**:
   ```bash
   mkdir -p ~/.picocloj/skills/my-skill
   ```

2. **Create SKILL.md** with your frontmatter and prompt

3. **Restart PicoClaw** or reload skills (currently requires restart)

4. **Activate your skill** with `/my-skill`

### Custom Tool Schemas

Skills can extend existing tool schemas using the `custom-tools` frontmatter field. This lets you:

- Add new parameters to existing tools
- Change tool descriptions
- Provide default values

**Important**: Custom tool schemas only modify the schema presented to the LLM. The underlying implementation remains the same. If a tool doesn't exist in the runtime, it will be skipped with a warning.

## Advanced Usage

### Tool Filtering

Skills can restrict which tools are available via the `allowed-tools` list:

```yaml
allowed-tools: [shell_command, simple_calc]
```

When this skill is active, only the listed tools will be available. If `allowed-tools` is empty or missing, all tools are available.

### Community Skills

PicoClaw skills are compatible with the broader Agent Skills ecosystem. You can use skills from:

- **ClawHub** (https://clawhub.tech) - Community skill registry
- **GitHub repositories** - Many skills are available as SKILL.md files
- **Other Claude Code agents** - Skills are portable across implementations

To use a community skill:
1. Download the SKILL.md file (and any supporting files)
2. Place it in your skills directory
3. Ensure tool names match your implementation

### Security Considerations

1. **Skill Loading**: Only load skills from trusted sources
2. **Tool Restrictions**: Use `allowed-tools` to limit capabilities
3. **Shell Commands**: The `shell-helper` skill includes safety guidelines but ultimate responsibility lies with the user
4. **Custom Tools**: Validate custom tool schemas before use

## Troubleshooting

### Skill Not Loading
- Check the SKILL.md file exists in `~/.picocloj/skills/skill-name/`
- Verify YAML frontmatter syntax (must start and end with `---`)
- Check for parsing errors in the agent output

### Tool Not Available
- Ensure tool name in `allowed-tools` matches registered tool name
- Check if custom tool schema references an existing tool
- Verify tool is registered in the runtime

### Permission Errors (Shell Commands)
- The agent runs with your user permissions
- Some commands may require elevated privileges (use with caution)
- Consider using the `dir` parameter to change working directories

## Examples Directory

Check the `~/.picocloj/skills/` directory for working examples:

1. **example/** - Basic skill structure
2. **shell-helper/** - Skill with tool restrictions and safety guidelines
3. **custom-tool-test/** - Skill demonstrating custom tool schemas

## Next Steps

- Explore community skills at ClawHub
- Create skills for your specific workflows (coding, writing, research)
- Combine multiple skills for complex tasks
- Contribute your skills back to the community

Skills make PicoClaw adaptable to any task while maintaining security and control. Start with the built-in examples, then create your own specialized assistants!