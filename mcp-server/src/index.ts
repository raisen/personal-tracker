import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { createServer } from "http";
import { loadGist, saveData, listTrackerGists } from "./gist.js";
import type { TrackerConfig, TrackerData, Entry } from "./gist.js";
import { z } from "zod";

// All config via env vars - set these in Render's environment settings
const GITHUB_TOKEN = process.env.GITHUB_TOKEN;
const GIST_ID = process.env.GIST_ID;
const PORT = parseInt(process.env.PORT || "3000", 10);
if (!GITHUB_TOKEN) {
  console.error("GITHUB_TOKEN environment variable is required");
  process.exit(1);
}

const server = new McpServer({
  name: "personal-tracker",
  version: "1.0.0",
});

// Helper to get token and gist ID, supporting per-request overrides
function getCredentials(overrideGistId?: string): {
  token: string;
  gistId: string;
} {
  const token = GITHUB_TOKEN!;
  const gistId = overrideGistId || GIST_ID;
  if (!gistId) {
    throw new Error(
      "No gist ID configured. Set GIST_ID env var or pass gist_id parameter."
    );
  }
  return { token, gistId };
}

// --- Tools ---

server.tool(
  "list_trackers",
  "List all personal tracker gists available in the connected GitHub account",
  {},
  async () => {
    const gists = await listTrackerGists(GITHUB_TOKEN!);
    return {
      content: [
        {
          type: "text",
          text: JSON.stringify(gists, null, 2),
        },
      ],
    };
  }
);

server.tool(
  "get_config",
  "Get the tracker configuration (fields, title, prompts)",
  { gist_id: z.string().optional().describe("Override the default GIST_ID") },
  async ({ gist_id }) => {
    const { token, gistId } = getCredentials(gist_id);
    const { config } = await loadGist(token, gistId);
    return {
      content: [{ type: "text", text: JSON.stringify(config, null, 2) }],
    };
  }
);

server.tool(
  "get_entries",
  "Get tracker entries, optionally filtered by date range or field values",
  {
    gist_id: z.string().optional().describe("Override the default GIST_ID"),
    from_date: z
      .string()
      .optional()
      .describe("Filter entries from this date (ISO format, inclusive)"),
    to_date: z
      .string()
      .optional()
      .describe("Filter entries up to this date (ISO format, inclusive)"),
    limit: z
      .number()
      .optional()
      .describe("Max number of entries to return (most recent first)"),
    field: z
      .string()
      .optional()
      .describe("Field name to filter by"),
    value: z
      .string()
      .optional()
      .describe("Value to match for the filtered field"),
  },
  async ({ gist_id, from_date, to_date, limit, field, value }) => {
    const { token, gistId } = getCredentials(gist_id);
    const { data } = await loadGist(token, gistId);

    let entries = [...data.entries].sort(
      (a, b) =>
        new Date(b._created).getTime() - new Date(a._created).getTime()
    );

    if (from_date) {
      const from = new Date(from_date).getTime();
      entries = entries.filter((e) => new Date(e._created).getTime() >= from);
    }
    if (to_date) {
      const to = new Date(to_date).getTime();
      entries = entries.filter((e) => new Date(e._created).getTime() <= to);
    }
    if (field && value !== undefined) {
      entries = entries.filter((e) => String(e[field]) === value);
    }
    if (limit) {
      entries = entries.slice(0, limit);
    }

    return {
      content: [
        {
          type: "text",
          text: JSON.stringify(
            { total: data.entries.length, returned: entries.length, entries },
            null,
            2
          ),
        },
      ],
    };
  }
);

server.tool(
  "add_entry",
  "Add a new tracker entry. Pass field values as key-value pairs in the 'fields' object.",
  {
    gist_id: z.string().optional().describe("Override the default GIST_ID"),
    fields: z
      .record(z.string(), z.unknown())
      .describe(
        "Key-value pairs for the entry fields (use field IDs from get_config)"
      ),
  },
  async ({ gist_id, fields }) => {
    const { token, gistId } = getCredentials(gist_id);
    const { data } = await loadGist(token, gistId);

    const now = new Date().toISOString();
    const entry: Entry = {
      _id: crypto.randomUUID(),
      _created: now,
      _updated: now,
      ...fields,
    };

    data.entries.push(entry);
    await saveData(token, gistId, data);

    return {
      content: [
        {
          type: "text",
          text: JSON.stringify({ success: true, entry }, null, 2),
        },
      ],
    };
  }
);

server.tool(
  "update_entry",
  "Update an existing tracker entry by ID",
  {
    gist_id: z.string().optional().describe("Override the default GIST_ID"),
    entry_id: z.string().describe("The _id of the entry to update"),
    fields: z
      .record(z.string(), z.unknown())
      .describe("Key-value pairs of fields to update"),
  },
  async ({ gist_id, entry_id, fields }) => {
    const { token, gistId } = getCredentials(gist_id);
    const { data } = await loadGist(token, gistId);

    const entry = data.entries.find((e) => e._id === entry_id);
    if (!entry) {
      return {
        content: [
          { type: "text", text: `Entry not found: ${entry_id}` },
        ],
        isError: true,
      };
    }

    Object.assign(entry, fields, { _updated: new Date().toISOString() });
    await saveData(token, gistId, data);

    return {
      content: [
        {
          type: "text",
          text: JSON.stringify({ success: true, entry }, null, 2),
        },
      ],
    };
  }
);

server.tool(
  "delete_entry",
  "Delete a tracker entry by ID",
  {
    gist_id: z.string().optional().describe("Override the default GIST_ID"),
    entry_id: z.string().describe("The _id of the entry to delete"),
  },
  async ({ gist_id, entry_id }) => {
    const { token, gistId } = getCredentials(gist_id);
    const { data } = await loadGist(token, gistId);

    const index = data.entries.findIndex((e) => e._id === entry_id);
    if (index === -1) {
      return {
        content: [
          { type: "text", text: `Entry not found: ${entry_id}` },
        ],
        isError: true,
      };
    }

    const [removed] = data.entries.splice(index, 1);
    await saveData(token, gistId, data);

    return {
      content: [
        {
          type: "text",
          text: JSON.stringify({ success: true, deleted: removed }, null, 2),
        },
      ],
    };
  }
);

server.tool(
  "get_summary",
  "Get a summary/statistics of tracker data for AI analysis",
  {
    gist_id: z.string().optional().describe("Override the default GIST_ID"),
    days: z
      .number()
      .optional()
      .describe("Number of recent days to summarize (default: all)"),
  },
  async ({ gist_id, days }) => {
    const { token, gistId } = getCredentials(gist_id);
    const { config, data } = await loadGist(token, gistId);

    let entries = data.entries;
    if (days) {
      const cutoff = new Date();
      cutoff.setDate(cutoff.getDate() - days);
      entries = entries.filter(
        (e) => new Date(e._created).getTime() >= cutoff.getTime()
      );
    }

    const summary: Record<string, unknown> = {
      tracker_title: config.title,
      total_entries: data.entries.length,
      filtered_entries: entries.length,
      fields: config.fields.map((f) => ({
        id: f.id,
        label: f.label,
        type: f.type,
      })),
    };

    if (entries.length > 0) {
      const dates = entries.map((e) => new Date(e._created).getTime());
      summary.date_range = {
        earliest: new Date(Math.min(...dates)).toISOString(),
        latest: new Date(Math.max(...dates)).toISOString(),
      };

      // Compute value distributions for select/radio fields
      const distributions: Record<string, Record<string, number>> = {};
      for (const field of config.fields) {
        if (field.type === "select" || field.type === "radio") {
          const counts: Record<string, number> = {};
          for (const entry of entries) {
            const val = String(entry[field.id] ?? "");
            if (val) counts[val] = (counts[val] || 0) + 1;
          }
          distributions[field.id] = counts;
        }
      }
      if (Object.keys(distributions).length > 0) {
        summary.distributions = distributions;
      }

      // Compute averages for number/range fields
      const averages: Record<string, number> = {};
      for (const field of config.fields) {
        if (field.type === "number" || field.type === "range") {
          const values = entries
            .map((e) => Number(e[field.id]))
            .filter((v) => !isNaN(v));
          if (values.length > 0) {
            averages[field.id] =
              Math.round(
                (values.reduce((a, b) => a + b, 0) / values.length) * 100
              ) / 100;
          }
        }
      }
      if (Object.keys(averages).length > 0) {
        summary.averages = averages;
      }
    }

    return {
      content: [{ type: "text", text: JSON.stringify(summary, null, 2) }],
    };
  }
);

// --- HTTP Server with Streamable HTTP Transport ---

const httpServer = createServer(async (req, res) => {
  if (req.url === "/health") {
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ status: "ok" }));
    return;
  }

  const parsedUrl = new URL(req.url || "/", `http://${req.headers.host}`);

  if (parsedUrl.pathname === "/mcp") {
    const transport = new StreamableHTTPServerTransport({
      sessionIdGenerator: undefined,
    });
    await server.connect(transport);

    if (req.method === "GET") {
      // Handle SSE connection for Streamable HTTP
      await transport.handleRequest(req, res);
      return;
    }

    const body = await new Promise<string>((resolve) => {
      let data = "";
      req.on("data", (chunk: Buffer) => {
        data += chunk.toString();
      });
      req.on("end", () => resolve(data));
    });

    const message = JSON.parse(body);
    await transport.handleRequest(req, res, message);
    return;
  }

  res.writeHead(404);
  res.end("Not found");
});

httpServer.listen(PORT, () => {
  console.log(`Personal Tracker MCP server running on port ${PORT}`);
  console.log(`MCP endpoint: POST /mcp`);
  console.log(`Health check: GET /health`);
  console.log(`GIST_ID: ${GIST_ID ? "configured" : "not set (pass per-request)"}`);
});
