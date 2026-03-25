---
title: "Discord"
description: "Connect your KiloClaw agent to Discord"
---

# Discord

Connect your KiloClaw agent to Discord by creating a bot in the Discord Developer Portal and linking it to your KiloClaw dashboard.

## Create an Application and Bot

1. Go to the [Discord Developer Portal](https://discord.com/developers/applications) and log in
2. Click **New Application**, give it a name, and click **Create**
3. Click **Bot** on the left sidebar
4. Click **Add Bot** and confirm

## Enable Privileged Intents

On the **Bot** page, scroll down to **Privileged Gateway Intents** and enable:

- **Message Content Intent** (required)
- **Server Members Intent** (recommended — needed for role allowlists and name matching)
- **Presence Intent** (optional)

## Copy Your Bot Token

1. Scroll back up on the **Bot** page and click **Reset Token**

> 📝 **Note**
> Despite the name, this generates your first token — nothing is being "reset."

2. Copy the token that appears and paste it into the **Discord Bot Token** field in your KiloClaw dashboard.

{% image src="/docs/img/kiloclaw/discord.png" alt="Connect account screen" width="800" caption="Discord bot token entry" /%}

Enter the token in the Settings tab and click **Save**. You can remove or replace a configured token at any time.

## Generate an Invite URL and Add the Bot to Your Server

1. Click **OAuth2** on the sidebar
2. Scroll down to **OAuth2 URL Generator** and enable:
   - `bot`
   - `applications.commands`
3. A **Bot Permissions** section will appear below. Enable:
   - View Channels
   - Send Messages
   - Read Message History
   - Embed Links
   - Attach Files
   - Add Reactions (optional)
4. Copy the generated URL at the bottom
5. Paste it into your browser, select your server, and click **Continue**
6. You should now see your bot in the Discord server

## Start Chatting with the Bot

1. Right-click on the Bot in Discord and click **Message**
2. DM the bot `/pair`
3. You should get a response back with a pairing code
4. Return to [app.kilocode.ai/claw](https://app.kilocode.ai/claw) and confirm the pairing code and approve
5. You should now be able to chat with the bot from Discord
