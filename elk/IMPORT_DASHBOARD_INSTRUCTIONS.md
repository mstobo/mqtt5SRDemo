# Import Pre-Built Dashboard - Step-by-Step Instructions

## What You'll Get

The pre-built dashboard includes 6 powerful visualizations:

1. **Validation Failure Rate Timeline** - Line chart showing failures over time
2. **Failures by Error Category** - Pie chart breaking down error types
3. **Publisher vs Subscriber Failures** - Bar chart comparing failure sources
4. **Validation Success Rate** - Metrics showing success vs failure counts
5. **Top Failing Sensors** - Table identifying problematic sensors
6. **Top Failing Clients** - Table showing which clients are having issues

---

## Import Steps

### Step 1: Open Kibana

Navigate to: **http://localhost:5601**

### Step 2: Go to Stack Management

1. Click the **☰ menu** (hamburger icon) in the top-left corner
2. Scroll down and click **"Stack Management"** (at the bottom of the menu)

### Step 3: Navigate to Saved Objects

1. In the left sidebar under "Kibana", click **"Saved Objects"**

### Step 4: Import the Dashboard

1. Click the **"Import"** button in the top-right
2. Click **"Import"** or drag and drop the file
3. Browse to: `/Users/matthewstobo/Documents/mqtt5SRDemo/elk/kibana-dashboard-import.ndjson`
4. Select the file and click **"Open"**

### Step 5: Resolve Import Conflicts (if any)

If you see any conflicts or warnings:
1. Select **"Automatically overwrite all saved objects"** (or)
2. Select **"Request action on each conflict"** and choose to overwrite

Click **"Import"**

### Step 6: View Your Dashboard

1. Click **"Done"** when the import completes
2. Click the **☰ menu** again
3. Click **"Dashboard"**
4. You should see: **"MQTT5 Schema Validation Operations Dashboard"**
5. Click on it to open!

---

## Dashboard Overview

### Layout (6 panels in a 2-column grid):

```
┌─────────────────────────────────────────────────────────────┐
│  Validation Failure Rate Timeline   │  Failures by Category  │
│  (Line chart - 24h view)            │  (Donut chart)         │
├─────────────────────────────────────────────────────────────┤
│  Publisher vs Subscriber Failures   │  Validation Success    │
│  (Bar chart)                        │  Rate (Metrics)        │
├─────────────────────────────────────────────────────────────┤
│  Top Failing Sensors                │  Top Failing Clients   │
│  (Data table)                       │  (Data table)          │
└─────────────────────────────────────────────────────────────┘
```

### Time Range

- **Default**: Last 24 hours
- **Auto-refresh**: Off (you can enable it in the top-right)
- You can change the time range using the date picker in the top-right corner

---

## Customizing the Dashboard

### Change Time Range
1. Click the **calendar icon** in the top-right
2. Select a preset (Last 15 minutes, Last 1 hour, Today, etc.)
3. Or create a custom range

### Enable Auto-Refresh
1. Click **"Refresh"** button in the top-right
2. Select an interval (5s, 10s, 30s, 1m, etc.)
3. Events will automatically update!

### Filter Data
1. Click on any value in the visualizations to filter
2. Use the **search bar** at the top to add KQL filters, e.g.:
   - `success:false` - Show only failures
   - `error_category:"value_out_of_range"` - Specific error type
   - `sensor_id:"sensor-001"` - Specific sensor
   - `client_type:PUBLISHER` - Only publisher failures

### Modify Visualizations
1. Click the **edit icon** (pencil) on any panel
2. Modify aggregations, colors, or layout
3. Click **"Save"** to keep changes

### Add New Panels
1. Click **"Edit"** in the top-right
2. Click **"Add panel"**
3. Create a new visualization or add existing ones

---

## Troubleshooting

### "No results found"
- **Cause**: No data in the selected time range
- **Fix**: 
  1. Change time range to "Last 24 hours" or wider
  2. Run your MQTT5 publisher/subscriber to generate events
  3. Check that events are in Discover first

### "Could not locate that index-pattern"
- **Cause**: Data view not created
- **Fix**: The import should create it automatically, but if not:
  1. Go to **Stack Management** → **Data Views**
  2. Click **"Create data view"**
  3. Index pattern: `mqtt5-validation-*`
  4. Time field: `@timestamp`
  5. Click **"Save"**

### Visualizations show errors
- **Cause**: Field mapping mismatch
- **Fix**:
  1. Go to **Discover**
  2. Verify the fields exist: `success`, `error_category`, `client_type`, `sensor_id`
  3. If fields are missing, re-run the publisher to generate new events

---

## Creating Alerts (Optional)

Want to get notified when validation failures spike?

### Step 1: Create Alert Rule
1. In the dashboard, click **☰ menu** → **Stack Management**
2. Under "Alerts and Insights", click **"Rules"**
3. Click **"Create rule"**

### Step 2: Configure Trigger
1. **Name**: "High Validation Failure Rate"
2. **Rule type**: "Elasticsearch query"
3. **Index**: `mqtt5-validation-*`
4. **Query**: `success:false`
5. **Threshold**: Count > 10 (in 5 minutes)

### Step 3: Add Actions
1. **Action**: Webhook, Email, Slack, etc.
2. **Message**: "Alert: {{context.hits}} validation failures in the last 5 minutes!"
3. Click **"Save"**

---

## Quick KQL Queries

Use these in the Kibana search bar:

### Show Only Failures
```
success:false
```

### Critical Errors Only
```
error_category:"schema_not_found" OR error_category:"connectivity_issue"
```

### Specific Sensor Failures
```
sensor_id:"sensor-001" AND success:false
```

### Publisher Errors Only
```
client_type:PUBLISHER AND success:false
```

### Temperature Out of Range
```
error_category:"value_out_of_range"
```

### Production Environment Errors
```
environment:production AND success:false
```

---

## Next Steps

1. **Monitor in Real-Time**: Enable auto-refresh and keep the dashboard open
2. **Set Up Alerts**: Configure notifications for high failure rates
3. **Share with Team**: Click "Share" → Copy link to share dashboard
4. **Export Reports**: Click "Share" → "PDF Reports" for scheduled exports
5. **Create Custom Views**: Clone and modify panels for specific use cases

---

## Support

For issues with:
- **Dashboard**: Check field names match your data in Discover
- **Visualizations**: Edit and adjust aggregations/filters
- **Data**: Verify events are flowing through Filebeat → Logstash → Elasticsearch

**Dashboard File Location**: 
`/Users/matthewstobo/Documents/mqtt5SRDemo/elk/kibana-dashboard-import.ndjson`

---

**Enjoy your operational visibility into MQTT5 schema validation!** 🚀

