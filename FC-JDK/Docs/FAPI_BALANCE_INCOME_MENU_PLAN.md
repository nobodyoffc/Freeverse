# FAPI Balance & Income Management Menu Plan

## Overview

This document outlines the plan to add a comprehensive menu item in `StartFapiServer.java` for managing:
1. **FUDP User Balances** - Balance management from FUDP protocol layer (via BalanceManager)
2. **Income Management** - Service income detection and tracking (via IncomeManager)
3. **Distribution Management** - Income distribution and settlement (via IncomeManager)

## Current State Analysis

### Existing Menu Items in StartFapiServer.java

Currently, the main menu has:
- ✅ "Manage service" - Service management
- ✅ "Update Income" - Manual income update (if IncomeManager exists)
- ✅ "Distribute Balance" - Manual distribution trigger (if IncomeManager exists)
- ✅ "Settle Payments" - Manual settlement trigger (if IncomeManager exists)
- ✅ "Update services" - Refresh service list
- ✅ "Settings" - General settings

### Available Components

1. **FudpNode** (accessible via `fudpNode` static variable):
   - `getBalanceManager()` - Access to FUDP balance management
   - `getBalanceMetrics()` - Balance metrics and statistics
   - `getHealthChecker()` - Health check functionality
   - `getAuditLog()` - Balance audit logs
   - `getBlacklist()` - Peer blacklist management
   - `getCreditLimitChecker()` - Credit limit management

2. **IncomeManager** (accessible via `settings.getManager(Manager.ManagerType.INCOME)`):
   - `updateIncome()` - Detect new income
   - `distribute()` - Distribute income
   - `settle()` - Settle payments
   - `getMyBalance()` - Get service balance
   - `getViaBalance()` - Get via balance
   - `getPayoffMap()` - Get pending payments

3. **Settings**:
   - Access to all managers
   - Configuration parameters

## Proposed Solution

### New Menu Structure

Add a new top-level menu item: **"Balance & Income Management"** with a comprehensive submenu.

```
FAPI Manager
├── Manage service
├── Balance & Income Management  [NEW]
│   ├── FUDP Balance Management
│   │   ├── View Provider Balances (consumers who owe us)
│   │   ├── View Consumer Balances (our balances with providers)
│   │   ├── View Balance Statistics
│   │   ├── Query Specific Peer Balance
│   │   ├── View Balance Audit Log
│   │   ├── Manage Credit Limits
│   │   └── Manage Blacklist
│   ├── Income Management
│   │   ├── Update Income (detect new income)
│   │   ├── View Income Statistics
│   │   ├── View Service Balance
│   │   ├── View Via Balances
│   │   └── View Pending Payments
│   ├── Distribution Management
│   │   ├── Distribute Balance (trigger distribution)
│   │   ├── Settle Payments (execute payments)
│   │   ├── View Distribution Settings
│   │   └── View Distribution History
│   └── Health & Monitoring
│       ├── Balance Health Check
│       ├── View Balance Metrics
│       └── View Alerts
├── Update services
└── Settings
```

## Implementation Plan

### Phase 1: Create Balance & Income Management Menu Class

**File**: `FC-JDK/src/main/java/fapi/menu/BalanceIncomeMenu.java`

**Purpose**: Centralized menu management for balance and income operations.

**Key Methods**:
```java
public class BalanceIncomeMenu {
    private final FudpNode fudpNode;
    private final IncomeManager incomeManager;
    private final Settings settings;
    private final BufferedReader br;
    
    public void showMenu() {
        Menu menu = new Menu("Balance & Income Management", () -> {});
        menu.add("FUDP Balance Management", this::showFudpBalanceMenu);
        menu.add("Income Management", this::showIncomeMenu);
        menu.add("Distribution Management", this::showDistributionMenu);
        menu.add("Health & Monitoring", this::showHealthMenu);
        menu.showAndSelect(br);
    }
    
    private void showFudpBalanceMenu() { ... }
    private void showIncomeMenu() { ... }
    private void showDistributionMenu() { ... }
    private void showHealthMenu() { ... }
}
```

### Phase 2: FUDP Balance Management Submenu

**Important Note**: Balance adjustments are **administrative operations** that should be:
- Restricted to authorized administrators
- Require explicit confirmation
- Logged to audit trail with reason
- Used only for corrections, settlements, or special cases
- Not used for normal operations (which are handled automatically)

**Features**:
1. **View Provider Balances**: List all consumers and their balances (what they owe us)
   - Use `balanceManager.getAllProviderBalances()` via `DualBalanceRecord`
   - Display: peerId, balance, sequenceNumber, creditLimit, lastUpdated
   - Support pagination for large lists
   - Quick filter buttons (over limit, high debt, recent activity)

2. **View Consumer Balances**: List all providers and our balances with them
   - Use `balanceManager.getAllConsumerBalances()` via `DualBalanceRecord`
   - Display: providerId, balance, expectedSequenceNumber, lastSynced
   - Support pagination for large lists
   - Quick filter buttons (high debt, recent sync, out of sync)

3. **View Balance Statistics**: Aggregate statistics
   - Total debt (negative balances)
   - Total credit (positive balances)
   - Number of active peers
   - Number of peers over credit limit
   - Use `balanceMetrics` if available

4. **Search & Filter Balances**: Advanced search and filtering
   - Search by peerId (partial match, case-insensitive)
   - Filter by balance range (e.g., show all with balance < -1000)
   - Filter by credit limit status (over limit, under limit, at limit)
   - Filter by last updated time (recent activity, inactive peers)
   - Filter by balance type (debt only, credit only, zero balance)
   - Sort results (by balance, by peerId, by lastUpdated)
   - Export search results

5. **Query Specific Peer Balance**: Query balance for a specific peer
   - Input peerId (with autocomplete from known peers)
   - Show both provider and consumer perspectives if applicable
   - Show related audit log entries for this peer

6. **View Balance Audit Log**: Recent balance changes
   - Use `auditLog.getRecentEntries(count)`
   - Display: timestamp, peerId, type, oldBalance, newBalance, amount, sequenceNumber

7. **Adjust User Balance**: Manual balance adjustment (administrative operation)
   - Select peer to adjust
   - Choose adjustment type:
     - Add debt (increase what they owe us) - use `balanceManager.addDebt()`
     - Reduce debt (decrease what they owe / add credit) - use `balanceManager.reduceDebt()`
     - Set absolute balance (set to specific value) - calculate difference and apply
   - Enter adjustment amount
   - Enter reason/note for adjustment (required for audit)
   - Show before/after balance preview
   - Require confirmation (with password or confirmation code)
   - Log to audit log with admin reason
   - Support batch adjustments (multiple peers at once)
   - View adjustment history

8. **Manage Credit Limits**: Set credit limits for peers
   - List peers with current credit limits
   - Search/filter peers by credit limit
   - Allow setting custom credit limit for a peer
   - Use `balanceManager.setCreditLimit(peerId, limit)`

9. **Manage Blacklist**: View and manage blacklisted peers
   - List all blacklisted peers
   - View blacklist reason and expiration
   - Remove from blacklist (manual override)
   - Use `blacklist.getAllBlacklistedPeers()`, `blacklist.removeFromBlacklist(peerId)`

### Phase 3: Income Management Submenu

**Features**:
1. **Update Income**: Manual trigger for income detection
   - Call `incomeManager.updateIncome()`
   - Display number of new incomes detected
   - Show summary of new income amounts

2. **View Income Statistics**: 
   - Service balance: `incomeManager.getMyBalance()`
   - Total income detected (from LocalDB)
   - Last income update time
   - Last income height

3. **View Service Balance**: Current service balance
   - Display: `incomeManager.getMyBalance()`
   - Show in FCH and satoshi

4. **View Via Balances**: Order via share balances
   - List all via balances: `incomeManager.getViaBalanceMapFromLocalDB()`
   - Show unpaid via balances: `incomeManager.getUnpaidViaBalance()`
   - Display: viaFid, balance, status (paid/unpaid)

5. **View Pending Payments**: Payments waiting to be settled
   - Display: `incomeManager.getPayoffMap()`
   - Show: recipient, amount, status

### Phase 4: Distribution Management Submenu

**Features**:
1. **Distribute Balance**: Trigger distribution calculation
   - Call `incomeManager.distribute()`
   - Display result (success/failed/skipped)
   - Show distribution summary if successful

2. **Settle Payments**: Execute pending payments
   - Call `incomeManager.settle()`
   - Display transaction ID if successful
   - Show error message if failed

3. **View Distribution Settings**:
   - Dealer min balance: `incomeManager.getDealerMinBalance()`
   - Min distribute balance: `incomeManager.getMinDistributeBalance()`
   - Distribute days: `incomeManager.getDistributeDays()`
   - Order via share: `incomeManager.getOrderViaShare()`
   - Last distribute time
   - Last settle height

4. **View Distribution History**:
   - Last settlement transaction ID (from LocalDB)
   - Last settlement height
   - Distribution statistics (total distributed, total settled)

### Phase 5: Health & Monitoring Submenu

**Features**:
1. **Balance Health Check**: Overall health status
   - Use `healthChecker.getHealthSummary()`
   - Display: status, issues, warnings, recommendations

2. **View Balance Metrics**: Performance and usage metrics
   - Use `balanceMetrics` to get statistics
   - Display: total requests, total fees, average fee, etc.

3. **View Alerts**: Active alerts and warnings
   - Use `alertManager.getActiveAlerts()`
   - Display: alert type, severity, message, timestamp

## Implementation Details

### File Structure

```
FC-JDK/src/main/java/fapi/
├── StartFapiServer.java (modify - add menu item)
└── menu/
    ├── BalanceIncomeMenu.java (new - main menu class)
    ├── FudpBalanceMenu.java (new - FUDP balance submenu)
    ├── IncomeMenu.java (new - income submenu)
    ├── DistributionMenu.java (new - distribution submenu)
    ├── HealthMenu.java (new - health submenu)
    ├── BalanceSearchFilter.java (new - search/filter utility)
    └── BalanceAdjustment.java (new - balance adjustment utility)
```

### Dependencies

**Required Access**:
- `fudpNode` (static in StartFapiServer) - for FUDP balance management
- `settings` (static in StartFapiServer) - for IncomeManager and other managers
- `br` (static in StartFapiServer) - for user input

**Null Safety**:
- Check if `fudpNode != null` before accessing balance management
- Check if `fudpNode.getBalanceManager() != null` before using
- Check if `incomeManager != null` before using
- Handle cases where balance management is disabled

### Error Handling

1. **Balance Management Disabled**: Show message that FUDP balance management is not enabled
2. **No Income Manager**: Show message that IncomeManager is not available
3. **No Peers**: Show message when no peers exist
4. **Database Errors**: Catch and display errors gracefully
5. **Invalid Input**: Validate user input and show error messages

### User Experience

1. **Clear Navigation**: Use consistent menu structure
2. **Informative Output**: Display data in readable format (tables, formatted numbers)
3. **Confirmation Prompts**: Ask for confirmation before destructive operations
4. **Progress Indicators**: Show progress for long-running operations
5. **Error Messages**: Clear, actionable error messages

## Integration Points

### StartFapiServer.java Changes

```java
// In main menu loop (around line 177)
menu.add("Balance & Income Management", () -> {
    BalanceIncomeMenu balanceMenu = new BalanceIncomeMenu(
        fudpNode, 
        incomeManager, 
        settings, 
        br
    );
    balanceMenu.showMenu();
});
```

### BalanceIncomeMenu.java Structure

```java
package fapi.menu;

import fudp.node.FudpNode;
import config.Settings;

import java.io.BufferedReader;

public class BalanceIncomeMenu {
    private final FudpNode fudpNode;
    private final IncomeManager incomeManager;
    private final Settings settings;
    private final BufferedReader br;

    public BalanceIncomeMenu(FudpNode fudpNode, IncomeManager incomeManager,
                             Settings settings, BufferedReader br) {
        this.fudpNode = fudpNode;
        this.incomeManager = incomeManager;
        this.settings = settings;
        this.br = br;
    }

    public void showMenu() {
        // Main menu implementation
    }

    // Submenu methods
    private void showFudpBalanceMenu() { ...}

    private void showIncomeMenu() { ...}

    private void showDistributionMenu() { ...}

    private void showHealthMenu() { ...}
}
```

## Testing Considerations

1. **Unit Tests**: Test each menu method independently
2. **Integration Tests**: Test with real FudpNode and IncomeManager
3. **Null Handling**: Test with null components
4. **Error Scenarios**: Test error handling paths
5. **User Input**: Test with various input scenarios

## Balance Adjustment Functionality Details

### Adjustment Types

1. **Add Debt** (Increase what peer owes us):
   - Use: `balanceManager.addDebt(peerId, amount)`
   - Effect: Balance becomes more negative (peer owes more)
   - Use cases: Manual fee correction, penalty, dispute resolution
   - Example: Balance -1000 → Add debt 500 → Balance -1500

2. **Reduce Debt** (Decrease what peer owes us / Add credit):
   - Use: `balanceManager.reduceDebt(peerId, amount)`
   - Effect: Balance becomes less negative or positive (peer owes less or has credit)
   - Use cases: Manual payment, refund, error correction, promotional credit
   - Example: Balance -1000 → Reduce debt 500 → Balance -500
   - Example: Balance -500 → Reduce debt 1000 → Balance +500 (credit)

3. **Set Absolute Balance** (Set to specific value):
   - Calculate difference: `adjustmentAmount = targetBalance - currentBalance`
   - If positive: use `reduceDebt(peerId, adjustmentAmount)`
   - If negative: use `addDebt(peerId, Math.abs(adjustmentAmount))`
   - Use cases: Complete reset, settlement, migration
   - Example: Balance -1000 → Set to 0 → Reduce debt 1000 → Balance 0

### Adjustment Workflow

```
1. Select Peer
   ├── Search/select from list
   ├── Or enter peerId directly
   └── Show current balance

2. Choose Adjustment Type
   ├── Add Debt
   ├── Reduce Debt
   └── Set Absolute Balance

3. Enter Details
   ├── Amount (in satoshi)
   ├── Reason (required, for audit)
   └── Optional: Reference ID or note

4. Preview
   ├── Current Balance: -1000
   ├── Adjustment: +500 (reduce debt)
   ├── New Balance: -500
   └── Warning if over credit limit

5. Confirmation
   ├── Show summary
   ├── Require password/confirmation
   └── Final confirmation prompt

6. Execute
   ├── Apply adjustment
   ├── Log to audit trail
   └── Show success/error message
```

### Safety Features

1. **Confirmation Requirements**:
   - Show clear before/after preview
   - Require explicit confirmation
   - Optional: Require password or admin code
   - Warn if adjustment would exceed credit limit
   - Warn if setting balance to unusual values

2. **Audit Logging**:
   - Log all adjustments with:
     - Timestamp
     - Admin user/identifier
     - Peer ID
     - Adjustment type
     - Amount
     - Before balance
     - After balance
     - Reason/note
     - Reference ID (if provided)

3. **Validation**:
   - Validate peerId exists
   - Validate amount is positive
   - Validate reason is not empty
   - Check credit limits (warn, don't block)
   - Prevent duplicate adjustments (optional: check recent history)

4. **Batch Adjustments**:
   - Support adjusting multiple peers at once
   - Use CSV or structured input
   - Show preview for all adjustments
   - Execute all or rollback all (atomic)
   - Individual success/failure tracking

### UI Flow

```
Adjust User Balance
├── Single Adjustment
│   ├── Select Peer: [Search/Select] or [Enter peerId]
│   ├── Current Balance: -1000 satoshi
│   ├── Adjustment Type: [Add Debt | Reduce Debt | Set Absolute]
│   ├── Amount: [______] satoshi
│   ├── Reason: [________________________________]
│   ├── Reference ID (optional): [________]
│   ├── Preview:
│   │   └── New Balance: -500 satoshi
│   ├── [Confirm] [Cancel]
│   └── Confirmation: [Type 'CONFIRM' to proceed]
│
└── Batch Adjustment
    ├── Input Method: [CSV File | Manual Entry]
    ├── Format: peerId,type,amount,reason
    ├── Preview List:
    │   ├── peer1: -1000 → -500 (reduce 500, reason: refund)
    │   ├── peer2: -2000 → 0 (set to 0, reason: settlement)
    │   └── ...
    ├── [Execute All] [Cancel]
    └── Results:
        ├── peer1: ✓ Success
        ├── peer2: ✓ Success
        └── peer3: ✗ Failed (peer not found)
```

### Implementation

```java
public class BalanceAdjustment {
    public enum AdjustmentType {
        ADD_DEBT,      // Increase debt
        REDUCE_DEBT,   // Decrease debt / add credit
        SET_ABSOLUTE   // Set to specific value
    }
    
    public static class AdjustmentRequest {
        private String peerId;
        private AdjustmentType type;
        private long amount;           // For ADD_DEBT/REDUCE_DEBT
        private long targetBalance;    // For SET_ABSOLUTE
        private String reason;
        private String referenceId;
        private String adminUser;
    }
    
    public static class AdjustmentResult {
        private boolean success;
        private String peerId;
        private long oldBalance;
        private long newBalance;
        private String errorMessage;
    }
    
    public AdjustmentResult adjustBalance(
        BalanceManager balanceManager,
        AdjustmentRequest request,
        String confirmationCode
    ) {
        // Validate confirmation
        if (!validateConfirmation(confirmationCode)) {
            return AdjustmentResult.failed("Invalid confirmation");
        }
        
        // Get current balance
        BalanceInfo current = balanceManager.getProviderBalanceInfo(request.peerId);
        long oldBalance = current.getBalance();
        
        // Calculate adjustment
        long adjustmentAmount = calculateAdjustment(request, oldBalance);
        
        // Apply adjustment
        boolean success;
        if (request.type == AdjustmentType.ADD_DEBT) {
            success = balanceManager.addDebt(request.peerId, adjustmentAmount);
        } else {
            balanceManager.reduceDebt(request.peerId, adjustmentAmount);
            success = true;
        }
        
        // Log to audit
        if (success && auditLog != null) {
            BalanceInfo newInfo = balanceManager.getProviderBalanceInfo(request.peerId);
            auditLog.logBalanceChange(
                request.peerId,
                BalanceChangeType.MANUAL_ADJUSTMENT,
                oldBalance,
                newInfo.getBalance(),
                adjustmentAmount,
                newInfo.getSequenceNumber(),
                request.reason,
                request.adminUser,
                request.referenceId
            );
        }
        
        return AdjustmentResult.success(...);
    }
}
```

### Use Cases

1. **Error Correction**:
   - Scenario: System incorrectly charged user
   - Action: Reduce debt by incorrect amount
   - Reason: "System error correction - incorrect fee calculation"

2. **Manual Settlement**:
   - Scenario: User paid via external method
   - Action: Reduce debt by payment amount
   - Reason: "External payment received - transaction ID: xxx"

3. **Refund**:
   - Scenario: Service issue, need to refund user
   - Action: Reduce debt or set to positive
   - Reason: "Service refund - issue ticket #123"

4. **Promotional Credit**:
   - Scenario: Marketing campaign, give users credit
   - Action: Reduce debt or set positive balance
   - Reason: "Promotional credit - campaign XYZ"

5. **Dispute Resolution**:
   - Scenario: User disputes charges
   - Action: Adjust based on resolution
   - Reason: "Dispute resolution - case #456"

6. **Migration/Reset**:
   - Scenario: System migration or account reset
   - Action: Set absolute balance to 0 or specific value
   - Reason: "Account migration - reset to zero"

## Search Functionality Details

### Search Features

1. **PeerId Search**:
   - Partial match (e.g., "ABC" matches "ABC123", "XYZABC")
   - Case-insensitive
   - Search in both provider and consumer balances
   - Highlight matching portion

2. **Balance Range Filter**:
   - Filter by minimum balance (e.g., show all with balance < -1000)
   - Filter by maximum balance (e.g., show all with balance > 10000)
   - Filter by exact balance
   - Filter by balance type:
     - Debt only (balance < 0)
     - Credit only (balance > 0)
     - Zero balance (balance == 0)
     - Over credit limit (balance < -creditLimit)

3. **Credit Limit Status Filter**:
   - Over limit (balance < -creditLimit)
   - Under limit (balance >= -creditLimit)
   - At limit (balance == -creditLimit)
   - No limit set (creditLimit == 0)

4. **Time-based Filter**:
   - Recent activity (lastUpdated within X hours/days)
   - Inactive peers (lastUpdated > X days ago)
   - Last updated between dates

5. **Sorting Options**:
   - By balance (ascending/descending)
   - By peerId (alphabetical)
   - By lastUpdated (recent first/last)
   - By sequenceNumber (high/low)

6. **Combined Search**:
   - Combine multiple filters (AND logic)
   - Save search criteria for reuse
   - Quick search presets (e.g., "High Debt Peers", "Inactive Peers")

### Implementation Approach

```java
public class BalanceSearchFilter {
    private String peerIdPattern;        // Partial match pattern
    private Long minBalance;             // Minimum balance
    private Long maxBalance;             // Maximum balance
    private BalanceType balanceType;     // DEBT, CREDIT, ZERO, OVER_LIMIT
    private CreditLimitStatus limitStatus; // OVER, UNDER, AT, NONE
    private Long minLastUpdated;         // Minimum lastUpdated timestamp
    private Long maxLastUpdated;         // Maximum lastUpdated timestamp
    private SortBy sortBy;               // BALANCE, PEER_ID, LAST_UPDATED, SEQUENCE
    private SortOrder sortOrder;         // ASC, DESC
    
    public List<BalanceSearchResult> search(
        Map<String, ProviderBalanceRecord> providerBalances,
        Map<String, ConsumerBalanceCache> consumerBalances
    ) {
        // Filter and sort logic
    }
}
```

### Search UI Flow

1. **Search Menu**:
   ```
   Search & Filter Balances
   ├── Quick Search (by peerId)
   ├── Advanced Search
   │   ├── PeerId Pattern: [________]
   │   ├── Balance Range: [min] to [max]
   │   ├── Balance Type: [Dropdown]
   │   ├── Credit Limit Status: [Dropdown]
   │   ├── Last Updated: [Date Range]
   │   ├── Sort By: [Dropdown]
   │   └── Sort Order: [ASC/DESC]
   ├── Saved Searches
   └── Search Presets
       ├── High Debt Peers (balance < -10000)
       ├── Over Credit Limit
       ├── Inactive Peers (no activity > 30 days)
       └── Recent Activity (activity < 24 hours)
   ```

2. **Search Results Display**:
   - Show matching count
   - Display results in table format
   - Pagination (e.g., 20 per page)
   - Export results option
   - Action buttons (view details, manage credit limit, etc.)

## Future Enhancements

1. **Export Functionality**: Export balance/income data to CSV/JSON
2. **Graphs/Charts**: Visual representation of balance trends
3. **Batch Operations**: Bulk credit limit updates, batch blacklist operations
4. **Scheduled Reports**: Generate periodic balance/income reports
5. **Notifications**: Alert on significant balance changes or income events
6. **Search History**: Remember recent searches
7. **Advanced Analytics**: Balance trends, peer behavior analysis

## Implementation Order

1. ✅ Create `BalanceIncomeMenu.java` skeleton
2. ✅ Create `BalanceSearchFilter.java` utility class
3. ✅ Create `BalanceAdjustment.java` utility class
4. ✅ Implement FUDP Balance Management submenu
   - Basic view operations
   - Search and filter functionality
   - Balance adjustment functionality
4. ✅ Implement Income Management submenu
5. ✅ Implement Distribution Management submenu
6. ✅ Implement Health & Monitoring submenu
7. ✅ Integrate into `StartFapiServer.java`
8. ✅ Add error handling and null checks
9. ✅ Test with enabled/disabled balance management
10. ✅ Test with/without IncomeManager
11. ✅ Test search functionality with various scenarios
12. ✅ Documentation and user guide

## Notes

- The menu should gracefully handle cases where balance management is disabled
- All operations should be read-only by default, with clear confirmation for write operations
- **Balance adjustments are sensitive operations** - require strong confirmation and audit logging
- **Search functionality is essential** when managing many peers (100+)
- Consider adding pagination for large lists (many peers)
- Format numbers appropriately (FCH, satoshi, percentages)
- Use consistent date/time formatting
- Search should be fast even with thousands of peers (in-memory filtering)
- Consider caching search results for repeated queries
- Provide clear feedback when no results match search criteria
- Balance adjustments should show clear before/after preview
- All adjustments must be logged with reason for audit trail
- Consider implementing role-based access control for balance adjustments (future enhancement)

