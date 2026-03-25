---
title: "Tools"
description: "Third-party tool integrations for your KiloClaw agent"
---

# Tools

## 1Password Integration Guide

Connect your KiloClaw agent to 1Password to securely manage credentials. This allows your agent to fetch API keys or passwords without ever seeing them in plain text.

### Step 1: Create a Dedicated Vault

For maximum security, do not give the bot access to your personal vault.

1. Log in to your 1Password account.
2. Create a **New Vault** (e.g., name it `Kilo-Agent-Vault`).
3. Move only the specific items/keys you want the bot to use into this vault.

### Step 2: Generate a Service Account Token

1. Go to the [1Password Developer Portal](https://developer.1password.com/).
2. Select **Service Accounts** and click **Create a Service Account**.
3. **Important:** When prompted for permissions, select only the dedicated vault you created in Step 1.
4. Copy the generated token (it will begin with `ops_`).

### Step 3: Configure KiloClaw

1. Navigate to your KiloClaw dashboard: [app.kilo.ai/claw](https://app.kilo.ai/claw).
2. Go to **Settings > Tools** (or **Edit Files**).
3. Paste your `ops_` token into the **1Password Setup** field.
4. Click **Save**.

### Step 4: Activate the Integration

To apply the changes and inject the 1Password CLI into your environment:

1. Select **Upgrade to latest**.
2. Perform a **Redeploy** to restart the agent with the new permissions active.

---

## Brave Search Integration

Equip your KiloClaw agent with real-time web browsing capabilities by integrating the Brave Search API. This allows the agent to fetch up-to-date information, perform market research, and verify facts beyond its training data.

### How to Generate a Brave Search API Key

To get started, you will need to obtain a "BSA" (Brave Search API) key from the Brave developer portal.

#### 1. Access the Brave Search Dashboard

Go to [api.search.brave.com](https://api.search.brave.com) and sign in or create a developer account.

#### 2. Choose a Subscription Plan

Brave Search API requires a paid subscription. Select the plan that fits your usage volume.

#### 3. Create an API Key

Once your account is active, navigate to the **API Keys** section and click **"Create New Key."**

#### 4. Copy the Key

Your key will typically begin with the prefix `BSA`. Copy this key immediately, as it may not be displayed again for security reasons.

---

## AgentCard Integration

Enable your KiloClaw agents to perform financial transactions by creating and managing virtual debit cards. This integration allows for automated purchasing and expense management within set limits.

> ⚠️ **Important:** This tool permits your agent to spend real money. Use extreme caution with budget limits. AgentCard is currently in Beta; card issuance may be limited or waitlisted.

### AgentCard Setup

#### 1. Create an AgentCard Account

Install the AgentCard CLI and sign up via your terminal:

```bash
agent-cards signup
```

#### 2. Add a Payment Method

Link your funding source (via Stripe) to enable the creation of virtual cards:

```bash
agent-cards payment-method
```

#### 3. Retrieve Your API Key

Open your local configuration file located at `~/.agent-cards/config.json`. Copy the value assigned to the `jwt` key.

#### 4. Configure KiloClaw

1. Paste the **JWT** into the AgentCard setup field in your KiloClaw settings.
2. Click **Save**.
3. Use **Redeploy** to apply the new secret. Only use **Upgrade & Redeploy** if you also need the latest platform version.

### Available Tools

Once activated, your agent will have access to:

- `create_card`: Generate a new virtual debit card.
- `list_cards`: View existing cards and their statuses.
- `check_balance`: Monitor available funds.
