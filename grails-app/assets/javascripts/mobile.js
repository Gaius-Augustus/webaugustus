
$(document).ready(function () {
	initNavigation();
	initMobileNavigation();
});

function initNavigation() {
	var layerOpen = false;
	var layerLevel2Open = false;

	$('.navigation-list__item[data-dropdown="true"] > a').on('click', function () {
		var _this = $(this);

		if (layerOpen) {
			if (_this.parent().hasClass('navigation-list__item--dropdown-open')) {
				_this.parent().toggleClass('navigation-list__item--dropdown-open');
				_this.parent().find('.navigation-list__dropdown').toggle();

				layerOpen = false;
			} else {
				$('.navigation-list__item--dropdown-open > .navigation-list__dropdown').stop().toggle();
				$('.navigation-list__item--dropdown-open').toggleClass('navigation-list__item--dropdown-open');

				layerOpen = false;
				_this.click();
				_this.parent().addClass('navigation-list__item--dropdown-open');
			}

			/** reset level-2 accordions **/
			$('.navigation-list__item--level-2').removeClass('navigation-list__item--dropdown-open').find('> .navigation-list').removeAttr('style');
			layerLevel2Open = false;

		} else {
			layerOpen = true;
			_this.parent('li').toggleClass('navigation-list__item--dropdown-open');
			_this.parent().find('.navigation-list__dropdown').toggle();
		}

		//close active header-submenu
		$('.header-submenu').find('.active').click();


		$('body').toggleClass('overlay');
		return false;
	});

	/** clickevent on document to close layer if open **/
	$(document).on('click touchstart', function (e) {
		if (layerOpen && e.which !== 3) {
			$('.navigation-list__item--dropdown-open > a').click();

			if($('#mobile-toggle').hasClass('mobile-toggle__icon--open')){
				$('#mobile-toggle').click();
			}
		}
	});

	/** stop click triggering on .navigation-section__layer-wrapper **/
	$('.navigation-list, .mobile-toggle').on('click touchstart', function (e) {
		e.stopPropagation();
	});


	/** level 2 accordion on mobile **/
	$('.navigation-list__headline').click(function (){
		if($(window).width() < 769){
			var _this = $(this);

			if(layerLevel2Open){
				if (_this.parent().hasClass('navigation-list__item--dropdown-open')) {
					_this.parent().toggleClass('navigation-list__item--dropdown-open');
					_this.parent().find('> .navigation-list').toggle();
					layerLevel2Open = false;
				} else {
					$('.navigation-list__item--level-2.navigation-list__item--dropdown-open').find('> .navigation-list').stop().toggle();
					$('.navigation-list__item--level-2.navigation-list__item--dropdown-open').removeClass('navigation-list__item--dropdown-open');
					layerLevel2Open = false;
					_this.click();
					_this.parent().addClass('navigation-list__item--dropdown-open');
				}

			}else{
				layerLevel2Open = true;
				_this.parent().toggleClass('navigation-list__item--dropdown-open');
				_this.parent().find('> .navigation-list').toggle();
			}
			return false;
		}
	});
}

function initMobileNavigation(){
	$('#mobile-toggle').click(function (){

		//close active mobile-slides
		if($('.mobile-toggle').find('.active').index() > -1){
			$('.mobile-toggle').find('.active').click();
			$('body,html').animate({
				scrollTop: 0
			}, 150);
		}

		//remove overlay if set or toggle mobile navigation
		if($('body').hasClass('overlay')){
			$(document).click();
		}else{
			$('.navigation').stop().slideToggle(150);
			$(this).toggleClass('mobile-toggle__icon--open').toggleClass('mobile-toggle__icon--close');
		}

		return false;
	});
}
(function ($){
	var $window = $(window);
	
	$(document).ready(function () {
        var winWidth = $window.width();
        
        var $navigationSub = $('.navigation-sub');
	    var $navigationSubHeadline = $('.navigation-sub__headline');
    
		var countSubPoints = $navigationSub.find('li ul.navigation-sub').children().length;
		var activeSubTitle = $navigationSub.find('li ul.navigation-sub li.navigation-sub__item--active').text();

		if (countSubPoints === 1 && $navigationSub.find('li ul.navigation-sub .navigation-sub__item--active').length) {
			$navigationSub.addClass('navigation-sub--single-active');
		}
		else {
			$navigationSub.find('span.navigation-sub__headline a').append('<span class="navigation-sub__headline--mobile">'+ activeSubTitle +'</span>');
		}

		$window.resize(function () {
			winWidth = $window.width();
		});

		$navigationSubHeadline.on('click', function () {
            $(this).siblings('.navigation-sub').toggleClass('navigation-sub--dropdown-open');
			$(this).toggleClass('navigation-sub--dropdown-close-icon');

			if (winWidth < 769) {
				return false;
			}
		});
	});
})(jQuery);
(function ($){
	var speed = 150;
	var active;
	var current;

	var $mobileClickButtons = $('.header-submenu__icon, .mobile-toggle__icon');
	var $mobileToggle = $('.mobile-toggle');
	var $closeButton = $('.close-button');

	function initHeadSubmenu(){
		$mobileClickButtons.click(function (){
			var data = $(this).data("content");

			if($('#'+data).index() > -1) {
				//closes active Slides
				$(this).parent().siblings().find('.active').click();

				//closes mobile-toggle
				$mobileToggle.find('.mobile-toggle__icon--open').click();

				//closes the main-menu if open
				if($('body').hasClass('overlay')){
					$(document).click();
				}

				$('#' + data).slideToggle(speed, function (){
					if(data === 'search'){
						$('#search-field').focus();
					}
				});
				$('[data-content="'+data+'"]').toggleClass('active');
			}

			return false;
		});

		$closeButton.click(function (){
			var id = $(this).parent().parent().attr('id');
			$('.header-submenu__icon[data-content="'+id+'"]').click();
			return false;
		});
	}

	$(document).ready(function(){
		initHeadSubmenu();
	});
})(jQuery);