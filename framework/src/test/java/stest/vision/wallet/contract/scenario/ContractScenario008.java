package stest.vision.wallet.contract.scenario;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.vision.api.GrpcAPI.AccountResourceMessage;
import org.vision.api.WalletGrpc;
import org.vision.common.crypto.ECKey;
import org.vision.common.utils.ByteArray;
import org.vision.common.utils.Utils;
import org.vision.core.Wallet;
import org.vision.protos.Protocol.Account;
import org.vision.protos.contract.SmartContractOuterClass.SmartContract;
import stest.vision.wallet.common.client.Configuration;
import stest.vision.wallet.common.client.Parameter.CommonConstant;
import stest.vision.wallet.common.client.utils.PublicMethed;

@Slf4j
public class ContractScenario008 {

  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] contract008Address = ecKey1.getAddress();
  String contract008Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private String fullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(1);
  private Long maxFeeLimit = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.maxFeeLimit");

  @BeforeSuite
  public void beforeSuite() {
    Wallet wallet = new Wallet();
    Wallet.setAddressPreFixByte(CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
  }

  /**
   * constructor.
   */

  @BeforeClass(enabled = true)
  public void beforeClass() {
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
  }

  @Test(enabled = true)
  public void deployErc721CryptoKitties() {
    ecKey1 = new ECKey(Utils.getRandom());
    contract008Address = ecKey1.getAddress();
    contract008Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
    PublicMethed.printAddress(contract008Key);
    Assert.assertTrue(PublicMethed.sendcoin(contract008Address, 5000000000L, fromAddress,
        testKey002, blockingStubFull));
    Assert.assertTrue(PublicMethed.freezeBalanceGetEntropy(contract008Address, 1000000L,
        3, 1, contract008Key, blockingStubFull));
    AccountResourceMessage accountResource = PublicMethed.getAccountResource(contract008Address,
        blockingStubFull);
    Long entropyLimit = accountResource.getEntropyLimit();
    Long entropyUsage = accountResource.getEntropyUsed();
    Account account = PublicMethed.queryAccount(contract008Key, blockingStubFull);
    logger.info("before balance is " + Long.toString(account.getBalance()));
    logger.info("before entropy limit is " + Long.toString(entropyLimit));
    logger.info("before entropy usage is " + Long.toString(entropyUsage));
    Long shortFeeLimit = 900L;

    String filePath = "./src/test/resources/soliditycode/contractScenario008.sol";
    String contractName = "KittyCore";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);

    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();
    byte[] contractAddress = PublicMethed.deployContract(contractName, abi, code, "", shortFeeLimit,
        0L, 100, null, contract008Key, contract008Address, blockingStubFull);

    contractAddress = PublicMethed.deployContract(contractName, abi, code, "", maxFeeLimit,
        0L, 100, null, contract008Key, contract008Address, blockingStubFull);

    final SmartContract smartContract = PublicMethed.getContract(contractAddress, blockingStubFull);
    accountResource = PublicMethed.getAccountResource(contract008Address, blockingStubFull);
    entropyLimit = accountResource.getEntropyLimit();
    entropyUsage = accountResource.getEntropyUsed();
    account = PublicMethed.queryAccount(contract008Key, blockingStubFull);
    logger.info("after balance is " + Long.toString(account.getBalance()));
    logger.info("after entropy limit is " + Long.toString(entropyLimit));
    logger.info("after entropy usage is " + Long.toString(entropyUsage));
    Assert.assertTrue(entropyLimit > 0);
    Assert.assertTrue(entropyUsage > 0);
    Assert.assertFalse(smartContract.getAbi().toString().isEmpty());
    Assert.assertTrue(smartContract.getName().equalsIgnoreCase(contractName));
    Assert.assertFalse(smartContract.getBytecode().toString().isEmpty());
  }

  /**
   * constructor.
   */

  @AfterClass
  public void shutdown() throws InterruptedException {
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}


