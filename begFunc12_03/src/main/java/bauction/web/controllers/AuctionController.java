package bauction.web.controllers;

import bauction.constants.StaticImagesConstants;
import bauction.domain.models.serviceModels.AuctionServiceModel;
import bauction.domain.models.serviceModels.participations.OfferServiceModel;
import bauction.domain.models.serviceModels.users.UserServiceModel;
import bauction.domain.models.serviceModels.participations.BiddingServiceModel;
import bauction.domain.models.viewModels.auctions.AuctionDetailsBuyerViewModel;
import bauction.domain.models.viewModels.auctions.AuctionDetailsViewModel;
import bauction.services.contracts.AuctionService;
import bauction.services.contracts.BiddingService;
import bauction.services.contracts.OfferService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;

@Controller
@RequestMapping("/auctions")
public class AuctionController extends BaseController{
    private final AuctionService auctionService;
    private final BiddingService biddingService;
    private final OfferService offerService;
    private final ModelMapper modelMapper;

    @Autowired
    public AuctionController(AuctionService auctionService, BiddingService biddingService, OfferService offerService, ModelMapper modelMapper) {
        this.auctionService = auctionService;
        this.biddingService = biddingService;
        this.offerService = offerService;
        this.modelMapper = modelMapper;
    }

    @GetMapping("/details/{id}")
    public ModelAndView  auctionDetails(@PathVariable(name = "id") String id, ModelAndView modelAndView){
        AuctionServiceModel found = this.auctionService.findById(id);
        this.auctionService.increaseAuctionViews(id);
        AuctionDetailsViewModel model=this.createDetailsViewModel(found);

        modelAndView.addObject("auctionDetails",model);
        modelAndView.setViewName("auction/auction-details");
        return modelAndView;
    }

    @PostMapping("/bidding/{id}")
    public ModelAndView makeBidding(@PathVariable(name = "id") String id, ModelAndView modelAndView,
                                    @RequestParam("price") BigDecimal biddingStep){
        AuctionServiceModel auction = this.auctionService.findById(id);
        UserServiceModel participant = this.modelMapper.map(super.getLoggedInUser(),UserServiceModel.class);

        BiddingServiceModel bidding=new BiddingServiceModel(new Date(),participant,auction,
                biddingStep, auction.getReachedPrice().add(biddingStep));

        this.biddingService.registerBidding(bidding);
        this.auctionService.increaseCurrentPrice(id, biddingStep);
        modelAndView.setViewName("redirect:/auctions/details/" + id);
        return modelAndView;
    }

    @PostMapping("/start/{id}")
    public ModelAndView startAuction(@PathVariable(name = "id") String id, ModelAndView modelAndView){
        AuctionServiceModel auction = this.auctionService.findById(id);
        this.auctionService.startAuction(auction);
        modelAndView.setViewName("redirect:/home");
        return modelAndView;
    }

    @PostMapping("/offers/{id}")
    public ModelAndView makeOffer(@PathVariable(name = "id") String id, ModelAndView modelAndView,
                                    @RequestParam("offerPrice") BigDecimal offeredPrice){
        AuctionServiceModel auction = this.auctionService.findById(id);
        UserServiceModel participant = this.modelMapper.map(super.getLoggedInUser(),UserServiceModel.class);
        OfferServiceModel offer=new OfferServiceModel(new Date(),participant,auction,offeredPrice);
        this.offerService.registerOffer(offer);

        modelAndView.setViewName("redirect:/auctions/details/" + id);
        return modelAndView;
    }


    private AuctionDetailsViewModel createDetailsViewModel(AuctionServiceModel found) {
        AuctionDetailsViewModel model = this.modelMapper.map(found, AuctionDetailsViewModel.class);
        if(model.getBuyer()==null){
            model.setBuyer(new AuctionDetailsBuyerViewModel());
        }
        model.setName(found.getProduct().getName());
        model.setDescription(found.getProduct().getDescription());
        model.setProductId(found.getProduct().getId());
        if(found.getEndDate()!=null){
            model.setRemainingTime(this.getFormedDateDifference(found.getEndDate()));
        }
        if(found.getProduct().getMainPicture()!=null){
            model.setMainImageUrl(found.getProduct().getMainPicture().getPath());
        }else {
            model.setMainImageUrl(StaticImagesConstants.DEFAULT_AUCTION_MAIN_IMAGE);
        }
        model.setTown(found.getProduct().getTown().getName());
        return model;
    }

    private String getFormedDateDifference(Date date) {
        SimpleDateFormat format = new SimpleDateFormat("MMM d', 'yyyy HH:mm:ss");
        return format.format(date);
    }
}
