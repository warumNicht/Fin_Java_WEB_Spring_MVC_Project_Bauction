package beginfunc.services;

import beginfunc.domain.entities.auctionRelated.Bidding;
import beginfunc.domain.models.serviceModels.participations.BiddingServiceModel;
import beginfunc.repositories.BiddingRepository;
import beginfunc.services.contracts.BiddingService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class BiddingServiceImpl implements BiddingService {
    private final BiddingRepository biddingRepository;
    private final ModelMapper modelMapper;

    @Autowired
    public BiddingServiceImpl(BiddingRepository biddingRepository, ModelMapper modelMapper) {
        this.biddingRepository = biddingRepository;
        this.modelMapper = modelMapper;
    }

    @Override
    public void registerBidding(BiddingServiceModel biddingServiceModel) {
        Bidding bidding = this.modelMapper.map(biddingServiceModel, Bidding.class);
        this.biddingRepository.saveAndFlush(bidding);
    }

    @Override
    public List<BiddingServiceModel> findAllBiddingsOfAuction(String auctionId) {
        List<BiddingServiceModel> biddings = this.biddingRepository.findAllBiddingsOfAuction(auctionId).stream()
                .map(b -> this.modelMapper.map(b, BiddingServiceModel.class))
                .collect(Collectors.toList());
        return biddings;
    }

    @Override
    public Long getAuctionBiddingCount(String id) {
        Long count = this.biddingRepository.getAuctionBiddingCount(id);
        return count == null ? 0 : count;
    }

    @Override
    public BiddingServiceModel findHighestBiddingOfAuction(String id) {
        List<Bidding> highestBiddingOfAuction =
                this.biddingRepository.findHighestBiddingOfAuction(id, PageRequest.of(0, 1));
        if (highestBiddingOfAuction.isEmpty()){
            return null;
        }
        return this.modelMapper.map(highestBiddingOfAuction.get(0),BiddingServiceModel.class);
    }

    @Override
    public void deleteBiddingsOfAuctionById(String id) {
        this.biddingRepository.deleteBiddingsOfAuctionById(id);
    }
}
