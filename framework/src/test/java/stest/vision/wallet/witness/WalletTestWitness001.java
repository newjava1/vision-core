package stest.vision.wallet.witness;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.spongycastle.util.encoders.Hex;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.vision.api.GrpcAPI;
import org.vision.api.GrpcAPI.NumberMessage;
import org.vision.api.GrpcAPI.Return;
import org.vision.api.WalletGrpc;
import org.vision.common.crypto.ECKey;
import org.vision.common.utils.ByteArray;
import org.vision.core.Wallet;
import org.vision.protos.Protocol.Account;
import org.vision.protos.Protocol.Block;
import org.vision.protos.Protocol.Transaction;
import org.vision.protos.contract.BalanceContract.FreezeBalanceContract;
import org.vision.protos.contract.BalanceContract.UnfreezeBalanceContract;
import org.vision.protos.contract.WitnessContract.VoteWitnessContract;
import stest.vision.wallet.common.client.Configuration;
import stest.vision.wallet.common.client.Parameter.CommonConstant;
import stest.vision.wallet.common.client.WalletClient;
import stest.vision.wallet.common.client.utils.Base58;
import stest.vision.wallet.common.client.utils.PublicMethed;
import stest.vision.wallet.common.client.utils.TransactionUtils;

@Slf4j
public class WalletTestWitness001 {

  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  private final String testKey003 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private final byte[] toAddress = PublicMethed.getFinalAddress(testKey003);
  private final String witnessKey001 = Configuration.getByPath("testng.conf")
      .getString("witness.key1");
  private final byte[] witnessAddress = PublicMethed.getFinalAddress(witnessKey001);


  private ManagedChannel channelFull = null;
  private ManagedChannel searchChannelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private WalletGrpc.WalletBlockingStub searchBlockingStubFull = null;
  private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
      .get(0);
  private String searchFullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(1);

  public static String loadPubKey() {
    char[] buf = new char[0x100];
    return String.valueOf(buf, 32, 130);
  }

  @BeforeSuite
  public void beforeSuite() {
    Wallet wallet = new Wallet();
    Wallet.setAddressPreFixByte(CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
  }

  /**
   * constructor.
   */

  @BeforeClass
  public void beforeClass() {

    WalletClient.setAddressPreFixByte(CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
    logger.info("Pre fix byte =====  " + WalletClient.getAddressPreFixByte());
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);

    searchChannelFull = ManagedChannelBuilder.forTarget(searchFullnode)
        .usePlaintext(true)
        .build();
    searchBlockingStubFull = WalletGrpc.newBlockingStub(searchChannelFull);
  }

  @Test(enabled = true)
  public void testVoteWitness() {
    String voteStr = Base58.encode58Check(witnessAddress);
    HashMap<String, String> smallVoteMap = new HashMap<String, String>();
    smallVoteMap.put(voteStr, "1");
    HashMap<String, String> wrongVoteMap = new HashMap<String, String>();
    wrongVoteMap.put(voteStr, "-1");
    HashMap<String, String> zeroVoteMap = new HashMap<String, String>();
    zeroVoteMap.put(voteStr, "0");

    HashMap<String, String> veryLargeMap = new HashMap<String, String>();
    veryLargeMap.put(voteStr, "1000000000");
    HashMap<String, String> wrongDropMap = new HashMap<String, String>();
    wrongDropMap.put(voteStr, "10000000000000000");

    //Vote failed due to no freeze balance.
    //Assert.assertFalse(VoteWitness(smallVoteMap, NO_FROZEN_ADDRESS, no_frozen_balance_testKey));

    //Freeze balance to get vote ability.
    Assert.assertTrue(PublicMethed.freezeBalance(fromAddress, 1200000L, 3L,
        testKey002, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    //Vote failed when the vote is large than the freeze balance.
    Assert.assertFalse(voteWitness(veryLargeMap, fromAddress, testKey002));
    //Vote failed due to 0 vote.
    Assert.assertFalse(voteWitness(zeroVoteMap, fromAddress, testKey002));
    //Vote failed duo to -1 vote.
    Assert.assertFalse(voteWitness(wrongVoteMap, fromAddress, testKey002));
    //Vote is so large, vote failed.
    Assert.assertFalse(voteWitness(wrongDropMap, fromAddress, testKey002));

    //Vote success
    Assert.assertTrue(voteWitness(smallVoteMap, fromAddress, testKey002));


  }

  /**
   * constructor.
   */

  @AfterClass
  public void shutdown() throws InterruptedException {
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
    if (searchChannelFull != null) {
      searchChannelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  /**
   * constructor.
   */

  public Boolean voteWitness(HashMap<String, String> witness, byte[] addRess, String priKey) {

    ECKey temKey = null;
    try {
      BigInteger priK = new BigInteger(priKey, 16);
      temKey = ECKey.fromPrivate(priK);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    ECKey ecKey = temKey;
    Account beforeVote = queryAccount(ecKey, blockingStubFull);
    Long beforeVoteNum = 0L;
    if (beforeVote.getVotesCount() != 0) {
      beforeVoteNum = beforeVote.getVotes(0).getVoteCount();
    }

    VoteWitnessContract.Builder builder = VoteWitnessContract.newBuilder();
    builder.setOwnerAddress(ByteString.copyFrom(addRess));
    for (String addressBase58 : witness.keySet()) {
      String value = witness.get(addressBase58);
      final long count = Long.parseLong(value);
      VoteWitnessContract.Vote.Builder voteBuilder = VoteWitnessContract.Vote
          .newBuilder();
      byte[] address = WalletClient.decodeFromBase58Check(addressBase58);
      logger.info("address ====== " + ByteArray.toHexString(address));
      if (address == null) {
        continue;
      }
      voteBuilder.setVoteAddress(ByteString.copyFrom(address));
      voteBuilder.setVoteCount(count);
      builder.addVotes(voteBuilder.build());
    }

    VoteWitnessContract contract = builder.build();

    Transaction transaction = blockingStubFull.voteWitnessAccount(contract);
    if (transaction == null || transaction.getRawData().getContractCount() == 0) {
      logger.info("transaction == null");
      return false;
    }
    transaction = signTransaction(ecKey, transaction);
    Return response = blockingStubFull.broadcastTransaction(transaction);

    if (response.getResult() == false) {
      logger.info(ByteArray.toStr(response.getMessage().toByteArray()));
      return false;
    }
    try {
      Thread.sleep(5000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    Account afterVote = queryAccount(ecKey, searchBlockingStubFull);
    //Long afterVoteNum = afterVote.getVotes(0).getVoteCount();
    for (String key : witness.keySet()) {
      for (int j = 0; j < afterVote.getVotesCount(); j++) {
        logger.info(Long.toString(Long.parseLong(witness.get(key))));
        logger.info(key);
        if (key.equals("TB4B1RMhoPeivkj4Hebm6tttHjRY9yQFes")) {
          logger.info("catch it");
          logger.info(Long.toString(afterVote.getVotes(j).getVoteCount()));
          logger.info(Long.toString(Long.parseLong(witness.get(key))));
          Assert
              .assertTrue(afterVote.getVotes(j).getVoteCount() == Long.parseLong(witness.get(key)));
        }

      }
    }
    return true;
  }

  /**
   * constructor.
   */

  public Boolean freezeBalance(byte[] addRess, long freezeBalance, long freezeDuration,
      String priKey) {
    byte[] address = addRess;
    long frozenBalance = freezeBalance;
    long frozenDuration = freezeDuration;

    //String priKey = testKey002;
    ECKey temKey = null;
    try {
      BigInteger priK = new BigInteger(priKey, 16);
      temKey = ECKey.fromPrivate(priK);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    ECKey ecKey = temKey;
    Account beforeFronzen = queryAccount(ecKey, blockingStubFull);

    Long beforeFrozenBalance = 0L;
    if (beforeFronzen.getFrozenCount() != 0) {
      beforeFrozenBalance = beforeFronzen.getFrozen(0).getFrozenBalance();
      logger.info(Long.toString(beforeFronzen.getFrozen(0).getFrozenBalance()));
    }

    FreezeBalanceContract.Builder builder = FreezeBalanceContract.newBuilder();
    ByteString byteAddreess = ByteString.copyFrom(address);

    builder.setOwnerAddress(byteAddreess).setFrozenBalance(frozenBalance)
        .setFrozenDuration(frozenDuration);

    FreezeBalanceContract contract = builder.build();

    Transaction transaction = blockingStubFull.freezeBalance(contract);

    if (transaction == null || transaction.getRawData().getContractCount() == 0) {
      return false;
    }

    transaction = TransactionUtils.setTimestamp(transaction);
    transaction = TransactionUtils.sign(transaction, ecKey);
    Return response = blockingStubFull.broadcastTransaction(transaction);

    if (response.getResult() == false) {
      return false;
    }

    Block currentBlock = blockingStubFull.getNowBlock(GrpcAPI.EmptyMessage.newBuilder().build());
    Block searchCurrentBlock = searchBlockingStubFull.getNowBlock(GrpcAPI
        .EmptyMessage.newBuilder().build());
    Integer wait = 0;
    while (searchCurrentBlock.getBlockHeader().getRawData().getNumber()
        < currentBlock.getBlockHeader().getRawData().getNumber() + 1 && wait < 30) {
      try {
        Thread.sleep(3000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      logger.info("Another fullnode didn't syn the first fullnode data");
      searchCurrentBlock = searchBlockingStubFull.getNowBlock(GrpcAPI
          .EmptyMessage.newBuilder().build());
      wait++;
      if (wait == 9) {
        logger.info("Didn't syn,skip to next case.");
      }
    }

    Account afterFronzen = queryAccount(ecKey, searchBlockingStubFull);
    Long afterFrozenBalance = afterFronzen.getFrozen(0).getFrozenBalance();
    logger.info(
        "afterfrozenbalance =" + Long.toString(afterFrozenBalance) + "beforefrozenbalance =  "
            + beforeFrozenBalance + "freezebalance = " + Long.toString(freezeBalance));
    Assert.assertTrue(afterFrozenBalance - beforeFrozenBalance == freezeBalance);
    return true;


  }

  /**
   * constructor.
   */

  public boolean unFreezeBalance(byte[] addRess, String priKey) {
    byte[] address = addRess;

    ECKey temKey = null;
    try {
      BigInteger priK = new BigInteger(priKey, 16);
      temKey = ECKey.fromPrivate(priK);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    ECKey ecKey = temKey;
    Account search = queryAccount(ecKey, blockingStubFull);

    UnfreezeBalanceContract.Builder builder = UnfreezeBalanceContract
        .newBuilder();
    ByteString byteAddreess = ByteString.copyFrom(address);

    builder.setOwnerAddress(byteAddreess);

    UnfreezeBalanceContract contract = builder.build();

    Transaction transaction = blockingStubFull.unfreezeBalance(contract);

    if (transaction == null || transaction.getRawData().getContractCount() == 0) {
      return false;
    }

    transaction = TransactionUtils.setTimestamp(transaction);
    transaction = TransactionUtils.sign(transaction, ecKey);
    Return response = blockingStubFull.broadcastTransaction(transaction);
    if (response.getResult() == false) {
      return false;
    } else {
      return true;
    }
  }

  /**
   * constructor.
   */

  public Account queryAccount(ECKey ecKey, WalletGrpc.WalletBlockingStub blockingStubFull) {
    byte[] address;
    if (ecKey == null) {
      String pubKey = loadPubKey(); //04 PubKey[128]
      if (StringUtils.isEmpty(pubKey)) {
        logger.warn("Warning: QueryAccount failed, no wallet address !!");
        return null;
      }
      byte[] pubKeyAsc = pubKey.getBytes();
      byte[] pubKeyHex = Hex.decode(pubKeyAsc);
      ecKey = ECKey.fromPublicOnly(pubKeyHex);
    }
    return grpcQueryAccount(ecKey.getAddress(), blockingStubFull);
  }

  public byte[] getAddress(ECKey ecKey) {
    return ecKey.getAddress();
  }

  /**
   * constructor.
   */

  public Account grpcQueryAccount(byte[] address, WalletGrpc.WalletBlockingStub blockingStubFull) {
    ByteString addressBs = ByteString.copyFrom(address);
    Account request = Account.newBuilder().setAddress(addressBs).build();
    return blockingStubFull.getAccount(request);
  }

  /**
   * constructor.
   */

  public Block getBlock(long blockNum, WalletGrpc.WalletBlockingStub blockingStubFull) {
    NumberMessage.Builder builder = NumberMessage.newBuilder();
    builder.setNum(blockNum);
    return blockingStubFull.getBlockByNum(builder.build());

  }

  private Transaction signTransaction(ECKey ecKey, Transaction transaction) {
    if (ecKey == null || ecKey.getPrivKey() == null) {
      logger.warn("Warning: Can't sign,there is no private key !!");
      return null;
    }
    transaction = TransactionUtils.setTimestamp(transaction);
    return TransactionUtils.sign(transaction, ecKey);
  }
}


