use 5.014004;
use ExtUtils::MakeMaker;
WriteMakefile(
    NAME => 'Jenkins::i18n',
    VERSION_FROM => 'lib/Jenkins/i18n.pm', # finds $VERSION, requires EU::MM from perl >= 5.5
    PREREQ_PM    => {},                    # e.g., Module::Name => 1.1
    ABSTRACT_FROM => 'lib/Jenkins/i18n.pm',    # retrieve abstract from module
    AUTHOR        => 'Alceu Rodrigue de Freitas Junior',
    PREREQ_PM     => { 'Config::Properties' => 1.80 },
    TEST_REQUIRES => {
        'Set::Tiny'           => 0.04,
        'Test::TempDir::Tiny' => 0.018,
        'Test::Exception'     => 0.43,
    },

    #LICENSE           => 'perl',
    #Value must be from legacy list of licenses here
    #https://metacpan.org/pod/Module::Build::API
);
