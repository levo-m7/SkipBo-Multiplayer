package org.skipbo;

import org.junit.jupiter.api.Test;

public class LobbyFoundationTest
{
    @Test
    void readyTogglesAndBroadcasts() throws Exception
    {
        LobbyChecks.readyTogglesAndBroadcasts();
    }

    @Test
    void profileLocksAndUnlocks() throws Exception
    {
        LobbyChecks.profileLocksAndUnlocks();
    }

    @Test
    void membershipAndCapacity() throws Exception
    {
        LobbyChecks.membershipAndCapacity();
    }

    @Test
    void anyRoomStopsAtSix() throws Exception
    {
        LobbyChecks.anyRoomStopsAtSix();
    }

    @Test
    void concurrentJoinsRespectCapacity() throws Exception
    {
        LobbyChecks.concurrentJoinsRespectCapacity();
    }

    @Test
    void concurrentJoinsKeepOneMembership() throws Exception
    {
        LobbyChecks.concurrentJoinsKeepOneMembership();
    }

    @Test
    void replacementRejectsOldSocket() throws Exception
    {
        LobbyChecks.replacementRejectsOldSocket();
    }

    @Test
    void reconnectPreservesHostAndReady() throws Exception
    {
        LobbyChecks.reconnectPreservesHostAndReady();
    }

    @Test
    void timeoutTransfersHost() throws Exception
    {
        LobbyChecks.timeoutTransfersHost();
    }

    @Test
    void lastTimeoutDeletesRoom() throws Exception
    {
        LobbyChecks.lastTimeoutDeletesRoom();
    }

    @Test
    void explicitLeaveIsImmediate() throws Exception
    {
        LobbyChecks.explicitLeaveIsImmediate();
    }

    @Test
    void validationRejectsBadValues() throws Exception
    {
        LobbyChecks.validationRejectsBadValues();
    }

    @Test
    void chatHistoryAndThrottle() throws Exception
    {
        LobbyChecks.chatHistoryAndThrottle();
    }

    @Test
    void globalListTracksMembership() throws Exception
    {
        LobbyChecks.globalListTracksMembership();
    }

    @Test
    void lifecycleFiltersListAndPreservesInGameSeat() throws Exception
    {
        LobbyChecks.lifecycleFiltersListAndPreservesInGameSeat();
    }

    @Test
    void deadSendsDoNotStopBroadcasts() throws Exception
    {
        LobbyChecks.deadSendsDoNotStopBroadcasts();
    }

    @Test
    void malformedWebSocketMessagesAreSafe() throws Exception
    {
        LobbyChecks.malformedWebSocketMessagesAreSafe();
    }

    @Test
    void sessionIdentityCannotBeForged() throws Exception
    {
        LobbyChecks.sessionIdentityCannotBeForged();
    }

    @Test
    void httpValidationAndResponse() throws Exception
    {
        LobbyChecks.httpValidationAndResponse();
    }

    @Test
    void httpProfileLockAndMalformedInput() throws Exception
    {
        LobbyChecks.httpProfileLockAndMalformedInput();
    }

    @Test
    void abandonedCreationExpires() throws Exception
    {
        LobbyChecks.abandonedCreationExpires();
    }

    @Test
    void explicitHostLeaveTransfersHost() throws Exception
    {
        LobbyChecks.explicitHostLeaveTransfersHost();
    }

    @Test
    void rejectedSocketJoinsAreGraceful() throws Exception
    {
        LobbyChecks.rejectedSocketJoinsAreGraceful();
    }

    @Test
    void oldTimeoutCannotRemoveNewMembership() throws Exception
    {
        LobbyChecks.oldTimeoutCannotRemoveNewMembership();
    }

    @Test
    void concurrentSessionInitializationKeepsIdentity() throws Exception
    {
        LobbyChecks.concurrentSessionInitializationKeepsIdentity();
    }

}
