package bauction.services;

import bauction.constants.ErrorMessagesConstants;
import bauction.domain.entities.auctionRelated.Auction;
import bauction.domain.entities.enums.AuctionStatus;
import bauction.domain.entities.enums.AuctionType;
import bauction.domain.entities.productRelated.products.BankNote;
import bauction.domain.entities.productRelated.products.BaseProduct;
import bauction.domain.entities.productRelated.products.Coin;
import bauction.domain.models.bindingModels.AuctionCreateBindingModel;
import bauction.domain.models.bindingModels.AuctionEditBindingModel;
import bauction.domain.models.bindingModels.collectionProducts.BanknoteBindingModel;
import bauction.domain.models.bindingModels.collectionProducts.BaseCollectionBindingModel;
import bauction.domain.models.bindingModels.collectionProducts.CoinBindingModel;
import bauction.domain.models.serviceModels.AuctionServiceModel;
import bauction.domain.models.serviceModels.CategoryServiceModel;
import bauction.domain.models.serviceModels.products.BanknoteServiceModel;
import bauction.domain.models.serviceModels.products.BaseProductServiceModel;
import bauction.domain.models.serviceModels.products.CoinServiceModel;
import bauction.domain.models.serviceModels.users.UserServiceModel;
import bauction.error.AuctionNotFoundException;
import bauction.repositories.AuctionRepository;
import bauction.services.contracts.*;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AuctionServiceImpl implements AuctionService {
    private final AuctionRepository auctionRepository;
    private final ProductService productService;
    private final OfferService offerService;
    private final BiddingService biddingService;
    private final CategoryService categoryService;
    private final ModelMapper modelMapper;

    @Autowired
    public AuctionServiceImpl(AuctionRepository auctionRepository, ProductService productService,
                              OfferService offerService, BiddingService biddingService, CategoryService categoryService, ModelMapper modelMapper) {
        this.auctionRepository = auctionRepository;
        this.productService = productService;
        this.offerService = offerService;
        this.biddingService = biddingService;
        this.categoryService = categoryService;
        this.modelMapper = modelMapper;
    }

    @Override
    public AuctionServiceModel createAuction(AuctionCreateBindingModel model,
                                             BaseCollectionBindingModel collectionBindingModel, UserServiceModel user) throws IOException {

        AuctionServiceModel auctionToSave = this.modelMapper.map(model, AuctionServiceModel.class);
        BaseProductServiceModel product = this.productService.createProduct(model, collectionBindingModel);
        auctionToSave.setProduct(product);
        auctionToSave.setSeller(user);
        auctionToSave.setCategory(this.categoryService.findByName(model.getCategory()));
        this.setAuctionPrices(auctionToSave, model);
        this.setAuctionStatus(auctionToSave, model);

        Auction auction = this.modelMapper.map(auctionToSave, Auction.class);
        this.correctModelMappersBug(auction);
        Auction savedAuction = this.auctionRepository.saveAndFlush(auction);
        return this.modelMapper.map(savedAuction, AuctionServiceModel.class);
    }

    @Override
    public void editAuction(AuctionServiceModel auctionToEdit, AuctionEditBindingModel model,
                            CoinBindingModel coin, BanknoteBindingModel banknote) throws IOException {
        BaseProductServiceModel changedProduct =
                this.productService.getChangedProduct(auctionToEdit, model, coin, banknote);
        changedProduct.setId(auctionToEdit.getProduct().getId());
        auctionToEdit.setProduct(changedProduct);
        auctionToEdit.setType(AuctionType.valueOf(model.getType()));
        auctionToEdit.setWantedPrice(model.getWantedPrice());

        if (!model.getCategory().equals(auctionToEdit.getCategory().getName())) {
            CategoryServiceModel editedCategory = this.categoryService.findByName(model.getCategory());
            auctionToEdit.setCategory(editedCategory);
        }
        Auction auction = this.modelMapper.map(auctionToEdit, Auction.class);
        this.correctModelMappersBug(auction);
        this.auctionRepository.saveAndFlush(auction);
    }

    @Override
    public void startAuction(AuctionServiceModel auction) {
        auction.setStatus(AuctionStatus.Active);
        auction.setStartDate(new Date());
        auction.setEndDate(this.getDateAfterDays(7));
        this.updateAuction(auction);
    }

    @Transactional
    @Override
    public void deleteById(String id) {
        Auction auction = this.auctionRepository.findById(id)
                .orElseThrow(() -> new AuctionNotFoundException(ErrorMessagesConstants.NOT_EXISTENT_AUCTION_MESSAGE));
        this.offerService.deleteOffersOfAuctionById(id);
        this.biddingService.deleteBiddingsOfAuctionById(id);
        this.auctionRepository.deleteById(id);
    }

    @Override
    public List<AuctionServiceModel> getActiveAuctionsOfUser(String userId) {
        return this.auctionRepository.getActiveAuctionsOfUser(userId).stream()
                .map(a -> this.modelMapper.map(a, AuctionServiceModel.class))
                .collect(Collectors.toList());
    }

    @Override
    public List<AuctionServiceModel> getFinishedAuctionsOfUserWithDeal(String userId) {
        return this.auctionRepository.getFinishedAuctionsOfUserWithDeal(userId).stream()
                .map(a -> this.modelMapper.map(a, AuctionServiceModel.class))
                .collect(Collectors.toList());
    }

    @Override
    public List<AuctionServiceModel> getFinishedAuctionsOfUserWithoutDeal(String userId) {
        return this.auctionRepository.getFinishedAuctionsOfUserWithoutDeal(userId).stream()
                .map(a -> this.modelMapper.map(a, AuctionServiceModel.class))
                .collect(Collectors.toList());
    }

    @Override
    public String getAuctionSellerId(String auctionId) {
        Auction auction = this.auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionNotFoundException(ErrorMessagesConstants.NOT_EXISTENT_AUCTION_MESSAGE));
        return auction.getSeller().getId();
    }

    @Override
    public List<AuctionServiceModel> findAllAuctionsByStatus(AuctionStatus status) {
        return this.auctionRepository
                .findAllByStatus(status).stream()
                .map(a -> this.modelMapper.map(a, AuctionServiceModel.class))
                .collect(Collectors.toList());
    }

    @Override
    public List<AuctionServiceModel> findAllActivesAuctionsExceedingEndDate() {
        List<AuctionServiceModel> auctionServiceModels =
                this.auctionRepository.findAllByStatusAndEndDateBefore(AuctionStatus.Active,new Date()).stream()
                        .map(a -> this.modelMapper.map(a, AuctionServiceModel.class))
                        .collect(Collectors.toList());
        return auctionServiceModels;
    }

    @Override
    public List<AuctionServiceModel> findAllFinishedAuctionsWithoutDeal() {
        return this.auctionRepository.findAllFinishedAuctionsWithoutDeal().stream()
                .map(a -> this.modelMapper.map(a, AuctionServiceModel.class))
                .collect(Collectors.toList());
    }

    @Override
    public List<AuctionServiceModel> getSortedAuctions(String category, String criteria) {
        criteria=" a.reachedPrice "+criteria.toUpperCase();
        List<Auction> sortedAuctions;
        Sort sort;

        if(criteria.toLowerCase().endsWith("ascending")){
            sort=Sort.by("reachedPrice").ascending();
        }else {
            sort=Sort.by("reachedPrice").descending();
        }

        if(category.toLowerCase().equals("all")){
            sortedAuctions=this.auctionRepository.getAllAuctionsSortedByPrice(sort);
        }else {
            sortedAuctions=this.auctionRepository.getAuctionsSortedByCategoryNameAndPrice(category, sort);
        }
        return sortedAuctions.stream()
                .map(a -> this.modelMapper.map(a, AuctionServiceModel.class))
                .collect(Collectors.toList());
    }

    @Override
    public List<AuctionServiceModel> getWaitingAuctionsOfUser(String userId) {
        List<Auction> waitingAuctionsOfUser = this.auctionRepository.getWaitingAuctionsOfUser(userId);
        return waitingAuctionsOfUser.stream()
                .map(a -> this.modelMapper.map(a, AuctionServiceModel.class))
                .collect(Collectors.toList());
    }

    @Override
    public AuctionServiceModel findById(String id) {
        Auction found = this.auctionRepository.findById(id)
                .orElseThrow(() -> new AuctionNotFoundException(ErrorMessagesConstants.NOT_EXISTENT_AUCTION_MESSAGE));
        return this.modelMapper.map(found, AuctionServiceModel.class);
    }

    @Override
    public void increaseAuctionViews(String id) {
        this.auctionRepository.increaseViews(id);
    }

    @Override
    public void increaseCurrentPrice(String id, BigDecimal biddingStep) {
        this.auctionRepository.increaseCurrentPrice(id, biddingStep);
    }

    @Override
    public void updateAuction(AuctionServiceModel model) {
        Auction auction = this.modelMapper.map(model, Auction.class);
        this.auctionRepository.saveAndFlush(auction);
    }


    private BaseProduct createBaseProductEntity(BaseProductServiceModel product) {
        if (product instanceof CoinServiceModel) {
            return this.modelMapper.map(product, Coin.class);
        } else if (product instanceof BanknoteServiceModel) {
            return this.modelMapper.map(product, BankNote.class);
        } else {
            return this.modelMapper.map(product, BaseProduct.class);
        }
    }

    private void setAuctionStatus(AuctionServiceModel auctionToSave, AuctionCreateBindingModel model) {
        if (model.isStartLater()) {
            auctionToSave.setStatus(AuctionStatus.Waiting);
        } else {
            auctionToSave.setStatus(AuctionStatus.Active);
            auctionToSave.setStartDate(new Date());
            auctionToSave.setEndDate(getDateAfterDays(7));
        }
    }

    private void setAuctionPrices(AuctionServiceModel auctionToSave, AuctionCreateBindingModel model) {
        BigDecimal wantedPrice = model.getWantedPrice();
        if (wantedPrice != null) {
            auctionToSave.setWantedPrice(wantedPrice);
            if (!auctionToSave.getType().equals(AuctionType.Preserved_Price)) {
                auctionToSave.setReachedPrice(wantedPrice);
            }
        }
    }

    private void correctModelMappersBug(Auction auctionToAdd) {
        BaseProduct product = auctionToAdd.getProduct();
        if (product.getMainPicture() != null) {
            product.getMainPicture().setProduct(product);
        }
        if (product.getPictures() != null) {
            product.getPictures().stream()
                    .forEach(p -> p.setProduct(product));
        }
    }

    private Date getDateAfterDays(int days) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.add(Calendar.DAY_OF_YEAR, days);
        return cal.getTime();
    }
}
