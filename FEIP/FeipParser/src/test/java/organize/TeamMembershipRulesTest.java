package organize;

import data.feipData.Team;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for the team membership branches of OrganizationParser.parseTeam.
 *
 * These call the extracted rules directly rather than parseTeam, because parseTeam reads and
 * writes the live `team` index -- there is no index-name indirection to point it at a scratch one.
 * What they pin down is each branch's decision (does this signer's op apply at all?) and the state
 * it leaves, which is where the fall-throughs used to go wrong.
 */
public class TeamMembershipRulesTest {

    private static final String OWNER = "F-owner";
    private static final String MANAGER = "F-manager";
    private static final String MEMBER = "F-member";
    private static final String OTHER = "F-other";
    private static final String INVITEE = "F-invitee";

    private static Team team() {
        Team t = new Team();
        t.setId("tid");
        t.setOwner(OWNER);
        t.setMembers(new String[]{OWNER, MANAGER, MEMBER});
        t.setMemberNum(3L);
        t.setManagers(new String[]{OWNER, MANAGER});
        t.setInvitees(new String[]{INVITEE, OTHER});
        t.setActive(true);
        return t;
    }

    private static Set<String> setOf(String[] values) {
        return values == null ? null : new TreeSet<>(Arrays.asList(values));
    }

    private static Set<String> set(String... values) {
        return new TreeSet<>(Arrays.asList(values));
    }

    // ---- withdraw invitation ----

    @Test
    @DisplayName("withdraw invitation by a non-manager is rejected and changes nothing (it used to fall through into join)")
    void withdrawByNonManagerIsRejected() {
        Team t = team();
        assertFalse(OrganizationParser.applyWithdrawInvitation(t, MEMBER, new String[]{INVITEE}));
        assertEquals(set(INVITEE, OTHER), setOf(t.getInvitees()));
    }

    @Test
    @DisplayName("withdraw invitation on a team with no managers list is rejected, not a crash")
    void withdrawWithNullManagersIsRejected() {
        Team t = team();
        t.setManagers(null);
        assertFalse(OrganizationParser.applyWithdrawInvitation(t, OWNER, new String[]{INVITEE}));
    }

    @Test
    void withdrawByManagerRemovesOnlyTheNamedInvitees() {
        Team t = team();
        assertTrue(OrganizationParser.applyWithdrawInvitation(t, MANAGER, new String[]{INVITEE, MEMBER}));
        assertEquals(set(OTHER), setOf(t.getInvitees()));
    }

    @Test
    void withdrawWithNoInviteesIsRejected() {
        Team t = team();
        t.setInvitees(null);
        assertFalse(OrganizationParser.applyWithdrawInvitation(t, MANAGER, new String[]{INVITEE}));
    }

    // ---- dismiss ----

    @Test
    @DisplayName("dismiss by a non-manager is rejected and changes nothing (it used to fall through into appoint)")
    void dismissByNonManagerIsRejected() {
        Team t = team();
        assertFalse(OrganizationParser.applyDismiss(t, MEMBER, new String[]{MANAGER}));
        assertEquals(set(OWNER, MANAGER, MEMBER), setOf(t.getMembers()));
        assertNull(t.getExMembers());
    }

    @Test
    @DisplayName("dismiss on a team with no managers list is rejected, not a NullPointerException")
    void dismissWithNullManagersIsRejected() {
        Team t = team();
        t.setManagers(null);
        assertFalse(OrganizationParser.applyDismiss(t, OWNER, new String[]{MEMBER}));
        assertEquals(set(OWNER, MANAGER, MEMBER), setOf(t.getMembers()));
    }

    @Test
    void dismissMovesMembersOutAndSkipsTheOwnerAndStrangers() {
        Team t = team();
        assertTrue(OrganizationParser.applyDismiss(t, MANAGER, new String[]{MEMBER, OWNER, OTHER}));
        assertEquals(set(OWNER, MANAGER), setOf(t.getMembers()));
        assertEquals(2L, t.getMemberNum());
        assertEquals(set(MEMBER), setOf(t.getExMembers()));
        assertEquals(set(OWNER, MANAGER), setOf(t.getManagers()));
    }

    @Test
    void dismissingAManagerAlsoEndsTheAppointment() {
        Team t = team();
        assertTrue(OrganizationParser.applyDismiss(t, OWNER, new String[]{MANAGER}));
        assertEquals(set(OWNER), setOf(t.getManagers()));
        assertEquals(set(MANAGER), setOf(t.getExMembers()));
    }

    // ---- leave ----

    @Test
    void leaveMovesTheSignerOutAndDropsTheirRole() {
        Team t = team();
        t.setExMembers(new String[]{OTHER});
        assertTrue(OrganizationParser.applyLeave(t, MANAGER));
        assertEquals(set(OWNER, MEMBER), setOf(t.getMembers()));
        assertEquals(2L, t.getMemberNum());
        assertEquals(set(OTHER, MANAGER), setOf(t.getExMembers()));
        assertEquals(set(OWNER), setOf(t.getManagers()));
    }

    @Test
    @DisplayName("leave does not apply to the owner, a non-member or an inactive team -- the parser used to bulk nothing and throw")
    void leaveDoesNotApplyWhereThereIsNothingToLeave() {
        Team owned = team();
        assertFalse(OrganizationParser.applyLeave(owned, OWNER));
        assertEquals(set(OWNER, MANAGER, MEMBER), setOf(owned.getMembers()));

        Team notIn = team();
        assertFalse(OrganizationParser.applyLeave(notIn, OTHER));
        assertNull(notIn.getExMembers());

        Team disbanded = team();
        disbanded.setActive(false);
        assertFalse(OrganizationParser.applyLeave(disbanded, MEMBER));
        assertEquals(set(OWNER, MANAGER, MEMBER), setOf(disbanded.getMembers()));
    }
}
